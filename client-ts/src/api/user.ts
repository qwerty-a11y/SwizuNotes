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

export function uploadAvatar(file: File): Promise<Result<string>> {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/users/avatar', formData)
}

export function avatarUrl(userId: number): string {
  return `/api/v1/users/${userId}/avatar`
}
