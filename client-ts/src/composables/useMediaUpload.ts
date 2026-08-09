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

import { ref } from 'vue'
import { uploadMedia } from '@/api/media'
import type { MediaCategory, MediaResponse } from '@/types/media'

export function useMediaUpload() {
  const uploading = ref(false)
  const error = ref('')

  async function upload(
    file: File,
    articleId: number,
    fileType: MediaCategory = 'image',
    metadata = '{}',
  ): Promise<MediaResponse> {
    uploading.value = true
    error.value = ''
    try {
      return (await uploadMedia(file, articleId, fileType, metadata)).data
    } catch (e) {
      error.value = (e as Error).message
      throw e
    } finally {
      uploading.value = false
    }
  }

  return { uploading, error, upload }
}
