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

import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import router from '@/router'
import type { Result } from '@/types/api'
import type { LoginResponse } from '@/types/media'

export const TOKEN_KEY = 'swizu_token'
export const REFRESH_TOKEN_KEY = 'swizu_refresh_token'

export const API_BASE: string = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export interface HttpInstance {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const instance = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<string> | null = null

function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken) throw new Error('无刷新令牌')
  const res = await axios.post<Result<LoginResponse>>(`${API_BASE}/session/refresh`, { refreshToken }, { timeout: 15000 })
  const data = res.data.data
  localStorage.setItem(TOKEN_KEY, data.accessToken)
  if (data.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken)
  }
  return data.accessToken
}

interface RetryConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

function redirectToLogin(): void {
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

instance.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const response = error.response
    const config: RetryConfig | undefined = error.config
    if (response?.status === 401 && config) {
      const isSessionApi = typeof config.url === 'string' && config.url.includes('/session/')
      const canRetry = !isSessionApi && !config._retried
      if (canRetry) {
        config._retried = true
        try {
          const token = await (refreshing ??= refreshAccessToken().finally(() => {
            refreshing = null
          }))
          config.headers.Authorization = `Bearer ${token}`
          return instance(config)
        } catch {
          clearAuth()
          redirectToLogin()
        }
      } else {
        clearAuth()
        redirectToLogin()
      }
    }
    const message: string | undefined = response?.data?.message
    return Promise.reject(new Error(message || `请求失败（${response?.status ?? '网络异常'}）`))
  },
)

const http = instance as unknown as HttpInstance

export default http
