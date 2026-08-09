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
  const mediaMap = new Map(mediaRefs.map((ref) => [ref.alias, ref.id]))
  const html = marked.parse(body || '', { async: false })
  return html.replace(/media:\/\/([a-z][a-z0-9_-]{0,31})/g, (match, alias: string) => {
    const id = mediaMap.get(alias)
    return id ? `/api/v1/media/${id}` : match
  })
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
