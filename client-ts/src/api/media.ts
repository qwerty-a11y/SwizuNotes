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

import http, { API_BASE, MEDIA_TOKEN_KEY } from './http'
import type { Result } from '@/types/api'
import type { MediaCategory, MediaResponse } from '@/types/media'

export function uploadMedia(
  file: File,
  articleId: number,
  fileType: MediaCategory,
  metadata = '{}',
): Promise<Result<MediaResponse>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('articleId', String(articleId))
  formData.append('fileType', fileType)
  formData.append('metadata', metadata)
  return http.post('/media/', formData)
}

export function deleteMedia(mediaId: string): Promise<void> {
  return http.delete(`/media/${mediaId}`)
}

export function getMediaInfo(mediaId: string): Promise<Result<MediaResponse>> {
  return http.get(`/media/${mediaId}/info`)
}

export function updateMediaMetadata(mediaId: string, metadata: string): Promise<Result<MediaResponse>> {
  return http.put(`/media/${mediaId}`, metadata, { headers: { 'Content-Type': 'application/json' } })
}

/** 媒体 URL（附加**媒体专用令牌** query 供 <img>/<audio>/<video> 加载草稿媒体；
 *  access token 不再进入 URL（避免 Referer/日志泄露）；公开媒体后端会忽略 token） */
export function mediaUrl(mediaId: string): string {
  const token = localStorage.getItem(MEDIA_TOKEN_KEY)
  return token
    ? `${API_BASE}/media/${mediaId}?token=${encodeURIComponent(token)}`
    : `${API_BASE}/media/${mediaId}`
}
