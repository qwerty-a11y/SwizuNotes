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

export type MediaCategory = 'image' | 'video' | 'audio' | 'file'

export interface Media {
  id: string
  articleId: number
  type: MediaCategory
  mimeType: string
  metadata: string
  size?: number | null
}

export type MediaResponse = Media

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  /** 媒体专用令牌（12 小时，仅媒体 URL query 使用） */
  mediaToken: string
  userId: number
}
