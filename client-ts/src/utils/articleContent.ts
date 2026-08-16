/*
 * Copyright (C) 2026 qwerty-a11y
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { API_BASE, MEDIA_TOKEN_KEY } from '@/api/http'
import { fileIconSvg } from '@/utils/fileIcons'
import type { ArticleContent, MediaRef } from '@/types/article'

export const MEDIA_ALIAS_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/

export function normalizeContent(content: ArticleContent | string | null | undefined): ArticleContent {
  if (!content) return { body: '', mediaRefs: [] }
  if (typeof content === 'string') {
    try {
      return JSON.parse(content) as ArticleContent
    } catch {
      return { body: content, mediaRefs: [] }
    }
  }
  return { body: content.body || '', mediaRefs: content.mediaRefs || [] }
}

export function renderBody(body: string, mediaRefs: MediaRef[] = []): string {
  const mediaMap = new Map(mediaRefs.map((ref) => [ref.alias, ref]))
  const token = localStorage.getItem(MEDIA_TOKEN_KEY)
  const toUrl = (id: string): string =>
    token ? `${API_BASE}/media/${id}?token=${encodeURIComponent(token)}` : `${API_BASE}/media/${id}`

  let headingIndex = 0
  const renderer = new marked.Renderer()
  const parser = new marked.Parser()
  renderer.heading = ({ tokens, depth }) => {
    headingIndex += 1
    const text = parser.parseInline(tokens)
    return `<h${depth} id="heading-${headingIndex}">${text}</h${depth}>`
  }

  const origLink = marked.Renderer.prototype.link
  const origImage = marked.Renderer.prototype.image
  renderer.link = (args) => {
    const ref = mediaRefFromHref(args.href, mediaMap)
    if (ref && ref.type !== 'image') {
      return mediaCardHtml(ref, toUrl(ref.id))
    }
    return origLink.call(renderer, args)
  }
  renderer.image = (args) => {
    const ref = mediaRefFromHref(args.href, mediaMap)
    if (ref && ref.type === 'image') {
      return `<img src="${toUrl(ref.id)}" alt="${escapeHtml(args.text)}" />`
    }
    return origImage.call(renderer, args)
  }

  // 渲染结果必须消毒后才能 v-html：marked 默认原样透传正文 HTML，
  // DOMPurify 默认配置会移除 <script>/<iframe>/<style>、全部 on* 事件属性、
  // javascript:/data: 等危险 URL（同时保留 media 卡片所需的 svg/button/data-* 结构）。
  // 后续若引入"脚本签名机制"，可在此按签名白名单放行特定 <script>。
  return DOMPurify.sanitize(marked.parse(body || '', { async: false, renderer }))
}

function mediaRefFromHref(href: string | null | undefined, mediaMap: Map<string, MediaRef>): MediaRef | undefined {
  if (!href) return undefined
  const m = /^media:\/\/([a-z][a-z0-9_-]{0,31})$/.exec(href)
  return m ? mediaMap.get(m[1]) : undefined
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 音频/视频/文件的自定义渲染（名称/封面由 info 接口异步填充），预览与正文共用 */
export function mediaCardHtml(ref: MediaRef, url: string): string {
  const type = ref.type
  if (type === 'video') {
    return `<div class="media-video" data-media-id="${escapeHtml(ref.id)}" data-url="${escapeHtml(url)}">
      <div class="media-video-player"></div>
      <span class="media-video-title">${escapeHtml(ref.alias)}</span>
    </div>`
  }
  const body =
    type === 'audio'
      ? `<div class="media-card-head">
           <span class="media-card-name">${escapeHtml(ref.alias)}</span>
           <a class="media-icon-btn" href="${escapeHtml(url)}" title="下载" aria-label="下载">
             <svg viewBox="0 0 1024 1024" aria-hidden="true"><path d="M853.333333 853.333333a42.666667 42.666667 0 0 1 0 85.333334H170.666667a42.666667 42.666667 0 0 1 0-85.333334h682.666666zM512 85.504a42.666667 42.666667 0 0 1 42.666667 42.666667v515.370666l204.373333-204.373333a42.666667 42.666667 0 0 1 63.914667 56.277333l-3.584 4.010667-277.376 277.546667a42.666667 42.666667 0 0 1-56.32 3.584l-4.010667-3.541334-277.12-276.650666a42.666667 42.666667 0 0 1 56.234667-63.957334l4.010666 3.541334L469.333333 644.096V128.170667a42.666667 42.666667 0 0 1 42.666667-42.666667z"/></svg>
           </a>
         </div>
         <div class="audio-player">
           <button type="button" class="audio-toggle" data-audio-action="toggle" aria-label="播放">
             <svg class="audio-icon-play" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>
             <svg class="audio-icon-pause" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 5h4v14H6zM14 5h4v14h-4z"/></svg>
           </button>
           <div class="audio-progress" data-audio-action="seek">
             <div class="audio-progress-track"><div class="audio-progress-fill"></div></div>
           </div>
           <span class="audio-time">0:00 / 0:00</span>
           <div class="audio-volume">
             <button type="button" class="audio-volume-toggle" data-audio-action="volume-toggle" aria-label="静音">
               <svg class="audio-icon-vol" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>
               <svg class="audio-icon-vol-muted" viewBox="0 0 24 24" aria-hidden="true"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>
             </button>
             <div class="audio-volume-pop">
               <div class="audio-volume-bar" data-audio-action="volume">
                 <div class="audio-volume-fill"></div>
               </div>
             </div>
           </div>
         </div>`
      : `<span class="media-card-name">${escapeHtml(ref.alias)}</span><span class="media-card-size"></span>`
  return `<div class="media-card media-${type}" data-media-id="${escapeHtml(ref.id)}" data-url="${escapeHtml(url)}">
    <div class="media-card-cover">${
      type === 'file'
        ? `<span class="media-card-file-icon">${fileIconSvg(ref.alias)}</span>`
        : `<span class="media-card-type">${type.toUpperCase()}</span>`
    }</div>
    <div class="media-card-body">${body}</div>
    ${type === 'file' ? `<a class="media-icon-btn" href="${escapeHtml(url)}" title="下载" aria-label="下载">
      <svg viewBox="0 0 1024 1024" aria-hidden="true"><path d="M853.333333 853.333333a42.666667 42.666667 0 0 1 0 85.333334H170.666667a42.666667 42.666667 0 0 1 0-85.333334h682.666666zM512 85.504a42.666667 42.666667 0 0 1 42.666667 42.666667v515.370666l204.373333-204.373333a42.666667 42.666667 0 0 1 63.914667 56.277333l-3.584 4.010667-277.376 277.546667a42.666667 42.666667 0 0 1-56.32 3.584l-4.010667-3.541334-277.12-276.650666a42.666667 42.666667 0 0 1 56.234667-63.957334l4.010666 3.541334L469.333333 644.096V128.170667a42.666667 42.666667 0 0 1 42.666667-42.666667z"/></svg>
    </a>` : ''}
  </div>`
}

export interface TocItem {
  id: string
  text: string
  level: number
}

/**
 * 提取目录（与 renderBody 的 heading id 计数**同源**）：
 * 直接走 marked.lexer 的同一份 token 流遍历 heading（含 Setext 标题、列表内标题），
 * 并天然跳过代码围栏内形如 `## x` 的行——与 renderBody 的 renderer.heading 计数完全一致，
 * 避免此前"行正则扫描"在代码块/Setext 标题下导致目录锚点错位。
 */
export function extractHeadings(body: string): TocItem[] {
  const items: TocItem[] = []
  let index = 0
  const walk = (tokens: marked.Token[]): void => {
    for (const token of tokens) {
      if (token.type === 'heading') {
        const heading = token as marked.Tokens.Heading
        index += 1
        items.push({ id: `heading-${index}`, text: heading.text.trim(), level: heading.depth })
      } else {
        const nested = (token as { tokens?: marked.Token[] }).tokens
        if (nested && nested.length) {
          walk(nested)
        }
      }
    }
  }
  walk(marked.lexer(body || ''))
  return items
}

export function uniqueAlias(mediaRefs: MediaRef[], prefix = 'img'): string {
  const used = new Set(mediaRefs.map((ref) => ref.alias))
  let index = mediaRefs.length + 1
  let alias = `${prefix}${index}`
  while (used.has(alias)) {
    index += 1
    alias = `${prefix}${index}`
  }
  return alias
}
