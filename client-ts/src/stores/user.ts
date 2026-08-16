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
import { login as loginApi, logout as logoutApi } from '@/api/session'
import { getMe } from '@/api/user'
import { invalidateAuth, MEDIA_TOKEN_KEY, REFRESH_TOKEN_KEY, TOKEN_KEY } from '@/api/http'

export const ACCOUNT_KEY = 'swizu_account'
export const USERNAME_KEY = 'swizu_username'
export const USER_ID_KEY = 'swizu_user_id'
export const IS_ADMIN_KEY = 'swizu_is_admin'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    account: localStorage.getItem(ACCOUNT_KEY) ?? '',
    username: localStorage.getItem(USERNAME_KEY) ?? '',
    userId: Number(localStorage.getItem(USER_ID_KEY)) || 0,
    /** 是否管理员（/users/me 拉取，管理页/管理入口判断） */
    isAdmin: localStorage.getItem(IS_ADMIN_KEY) === '1',
    /** 头像更新后 +1（附加到头像 URL 防缓存，导航栏/主页同步刷新） */
    avatarVersion: 0,
  }),
  getters: {
    isLoggedIn: (state): boolean => Boolean(state.token),
  },
  actions: {
    bumpAvatar(): void {
      this.avatarVersion += 1
    },
    async login(account: string, password: string): Promise<void> {
      const result = await loginApi(account, password)
      this.token = result.data.accessToken
      this.account = account
      this.userId = result.data.userId
      localStorage.setItem(TOKEN_KEY, result.data.accessToken)
      localStorage.setItem(REFRESH_TOKEN_KEY, result.data.refreshToken)
      localStorage.setItem(MEDIA_TOKEN_KEY, result.data.mediaToken)
      localStorage.setItem(ACCOUNT_KEY, account)
      localStorage.setItem(USER_ID_KEY, String(result.data.userId))
      // 登录响应不含昵称/管理员标记，拉取补齐
      await this.ensureUserId(true)
    },
    logout(): void {
      // 尽力吊销后端令牌（access 由 axios 拦截器自动携带；失败不阻断本地清理）
      const refresh = localStorage.getItem(REFRESH_TOKEN_KEY)
      const media = localStorage.getItem(MEDIA_TOKEN_KEY)
      // 先自增会话代数：在途的令牌刷新将放弃写回（防"退出后悄悄重新登录"）
      invalidateAuth()
      if (refresh) {
        logoutApi(refresh, media ?? undefined).catch(() => {})
      }
      this.token = ''
      this.account = ''
      this.username = ''
      this.userId = 0
      this.isAdmin = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem(MEDIA_TOKEN_KEY)
      localStorage.removeItem(ACCOUNT_KEY)
      localStorage.removeItem(USERNAME_KEY)
      localStorage.removeItem(USER_ID_KEY)
      localStorage.removeItem(IS_ADMIN_KEY)
    },
    /** 从后端纠正本地 userId（force 时强制校验，覆盖可能过期的本地值，防"本人主页"误判） */
    async ensureUserId(force = false): Promise<void> {
      if (!this.token) return
      if (this.userId && !force) return
      try {
        const me = (await getMe()).data
        this.userId = me.id
        this.account = me.account
        this.username = me.username
        this.isAdmin = me.isAdmin === true
        localStorage.setItem(USER_ID_KEY, String(me.id))
        localStorage.setItem(ACCOUNT_KEY, me.account)
        localStorage.setItem(USERNAME_KEY, me.username)
        localStorage.setItem(IS_ADMIN_KEY, this.isAdmin ? '1' : '0')
      } catch {
        // 未登录/令牌失效等情况静默忽略，由 401 拦截器统一处理
      }
    },
  },
})
