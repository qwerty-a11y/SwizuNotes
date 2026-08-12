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
import { login as loginApi } from '@/api/session'
import { REFRESH_TOKEN_KEY, TOKEN_KEY } from '@/api/http'

export const ACCOUNT_KEY = 'swizu_account'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    account: localStorage.getItem(ACCOUNT_KEY) ?? '',
  }),
  getters: {
    isLoggedIn: (state): boolean => Boolean(state.token),
  },
  actions: {
    async login(account: string, password: string): Promise<void> {
      const result = await loginApi(account, password)
      this.token = result.data.accessToken
      this.account = account
      localStorage.setItem(TOKEN_KEY, result.data.accessToken)
      localStorage.setItem(REFRESH_TOKEN_KEY, result.data.refreshToken)
      localStorage.setItem(ACCOUNT_KEY, account)
    },
    logout(): void {
      this.token = ''
      this.account = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem(ACCOUNT_KEY)
    },
  },
})
