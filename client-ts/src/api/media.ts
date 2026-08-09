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

import http from './http'
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

export function mediaUrl(mediaId: string): string {
  return `/api/v1/media/${mediaId}`
}
