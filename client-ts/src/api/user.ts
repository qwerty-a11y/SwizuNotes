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

import http, { API_BASE } from './http'
import type { Result } from '@/types/api'
import type { CurrentUser } from '@/types/user'

export function getMe(): Promise<Result<CurrentUser>> {
  return http.get('/users/me')
}

export function getUserProfile(userId: number): Promise<Result<CurrentUser>> {
  return http.get(`/users/${userId}/profile`)
}

export function updateUsername(username: string): Promise<Result<CurrentUser>> {
  return http.put('/users/me', { username })
}

export function uploadAvatar(file: File): Promise<Result<string>> {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/users/avatar', formData)
}

export function avatarUrl(userId: number): string {
  return `${API_BASE}/users/${userId}/avatar`
}

/** 头像加载失败时的统一兜底图（本地静态 SVG 占位图，与后端无头像默认图同款） */
export const DEFAULT_AVATAR_URL = '/images/default-avatar.svg'

/** <img> 头像加载失败 → 切换为默认 SVG 占位图（各处头像 fallback 统一入口） */
export function avatarErrorFallback(event: Event): void {
  const img = event.target as HTMLImageElement
  if (img && img.src !== DEFAULT_AVATAR_URL) {
    img.src = DEFAULT_AVATAR_URL
  }
}
