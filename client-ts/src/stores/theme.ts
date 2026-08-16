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

import { defineStore } from 'pinia'
import { API_BASE } from '@/api/http'
import { createPreviewToken } from '@/api/theme'
import { THEME_CHANGE_EVENT, THEME_LINK_ID, getDarkVariant, getStoredTheme } from '@/theme'

/** 主题预览会话（持久化到 localStorage，跨路由/刷新保持预览） */
export interface ThemePreviewSession {
  /** 主题名 */
  name: string
  displayName: string
  /** 是否公开（未公开主题的预览横幅需提示） */
  published: boolean
  /** 预览令牌（10 分钟有效，过期后无法恢复） */
  token: string
  /** 预览前的 theme-link href（退出预览时恢复；仅首次应用时记录） */
  savedHref: string
  /** 是否预览深色变体（切换深浅用；缺省 = 浅色主 CSS） */
  dark?: boolean
}

/** 预览会话持久化键 */
export const PREVIEW_KEY = 'swizu_preview'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    /** 当前预览会话（null = 未在预览） */
    preview: null as ThemePreviewSession | null,
    /** 当前生效的预览 blob URL（退出/替换时 revoke） */
    previewBlobUrl: '',
    /**
     * 已发布主题数量（导航栏布局依据：> 0 显示主题菜单并隐藏深浅按钮，
     * 深浅切换集成在菜单内；= 0 只显示深浅按钮）。
     * 由 NavBar 挂载时与 ThemePicker 加载时刷新。
     */
    publicThemeCount: 0,
  }),
  actions: {
    /** 刷新已发布主题数量（导航栏/主题选择器共用） */
    setPublicThemeCount(count: number): void {
      this.publicThemeCount = count
    },
    /**
     * 应用预览：拿令牌 fetch CSS（未发布主题后端已把 url() 重写为带令牌的地址）
     * → blob URL 应用到 <link id="theme-link">，整站立即切换（仅本管理员可见）。
     * 深浅变体：session.dark 时取专属变体 CSS（<name>-dark.css，令牌通用——
     * 变体归属同一主题）；无专属变体（getDarkVariant → 全局 dark）用公开的 dark.css。
     * 令牌失效/主题不存在时抛错（调用方处理并清理会话）。
     */
    async applyPreview(session: ThemePreviewSession): Promise<void> {
      const variant = getDarkVariant(session.name)
      const fileId = session.dark
        ? variant === 'dark'
          ? 'dark.css'
          : `${session.name}-dark.css`
        : `${session.name}.css`
      const res = await fetch(`${API_BASE}/static-resources/${fileId}?preview_token=${session.token}`)
      if (!res.ok) throw new Error('预览失败（令牌已过期或主题文件不存在）')
      const css = await res.text()
      const link = document.getElementById(THEME_LINK_ID) as HTMLLinkElement | null
      if (!link) return
      if (this.previewBlobUrl) URL.revokeObjectURL(this.previewBlobUrl)
      this.previewBlobUrl = URL.createObjectURL(new Blob([css], { type: 'text/css' }))
      // 仅首次应用记录退出恢复点（预览间切换主题/深浅不覆盖，否则退出会恢复到已 revoke 的旧 blob）
      if (!this.preview) {
        session.savedHref = link.href
      }
      // 预览切换同样播放整页过渡动画（View Transitions，与 applyTheme 一致；无该 API 或
      // prefers-reduced-motion 时直接切换）。blob CSS 加载极快，load 后即拍新快照。
      const supportsVT = typeof document.startViewTransition === 'function'
      const prefersReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false
      if (supportsVT && !prefersReduced) {
        document.startViewTransition(() => {
          link.href = this.previewBlobUrl
          return new Promise<void>((resolve) => {
            let settled = false
            const done = (): void => {
              if (!settled) {
                settled = true
                resolve()
              }
            }
            link.addEventListener('load', done, { once: true })
            // 极端情况兜底（blob 加载不会失败，load 应总会触发）
            window.setTimeout(done, 500)
          })
        })
      } else {
        link.href = this.previewBlobUrl
      }
      this.preview = session
      localStorage.setItem(PREVIEW_KEY, JSON.stringify(session))
      // 事件名用实际预览的文件主题名（深色变体派发 <name>-dark / dark），供组件同步深浅状态
      const appliedName = session.dark ? fileId.replace(/\.css$/, '') : session.name
      window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail: { name: appliedName } }))
    },

    /** 预览深浅切换：深色变体 <name>-dark.css ↔ 主 CSS（浮条按钮；失败时预览保持原样） */
    async togglePreviewDark(): Promise<void> {
      if (!this.preview) return
      this.preview.dark = !this.preview.dark
      await this.applyPreview(this.preview)
    },

    /** 开始预览（管理页）：申请预览令牌并应用 */
    async startPreview(name: string, displayName: string, published: boolean): Promise<void> {
      const { data } = await createPreviewToken(name)
      const session: ThemePreviewSession = { name, displayName, published, token: data.token, savedHref: '' }
      await this.applyPreview(session)
    },

    /** 退出预览：恢复预览前 href，清除会话与 blob */
    exitPreview(): void {
      const link = document.getElementById(THEME_LINK_ID) as HTMLLinkElement | null
      if (this.preview && link) {
        link.href = this.preview.savedHref
        window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail: { name: getStoredTheme() } }))
      }
      if (this.previewBlobUrl) {
        URL.revokeObjectURL(this.previewBlobUrl)
        this.previewBlobUrl = ''
      }
      this.preview = null
      localStorage.removeItem(PREVIEW_KEY)
    },

    /** 应用启动时恢复预览（跨刷新持久）：令牌有效则重新应用，失效则清理会话 */
    async restorePreview(): Promise<void> {
      const raw = localStorage.getItem(PREVIEW_KEY)
      if (!raw) return
      let session: ThemePreviewSession
      try {
        session = JSON.parse(raw) as ThemePreviewSession
      } catch {
        localStorage.removeItem(PREVIEW_KEY)
        return
      }
      if (!session?.name || !session.token) {
        localStorage.removeItem(PREVIEW_KEY)
        return
      }
      try {
        await this.applyPreview(session)
      } catch {
        // 令牌过期或主题被删：清理会话，保持当前主题
        localStorage.removeItem(PREVIEW_KEY)
      }
    },
  },
})
