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
import type { CreateThemePayload, ThemeAdmin, ThemeSummary } from '@/types/theme'

/** 公开主题列表（导航栏主题选择器） */
export function getPublicThemes(): Promise<Result<ThemeSummary[]>> {
  return http.get('/themes/')
}

/** 今天生效的日期主题（无日期主题命中时固定返回默认主题，恒非 null） */
export function getActiveTheme(): Promise<Result<ThemeSummary>> {
  return http.get('/themes/active')
}

/** 全部主题（含未公开，管理页） */
export function getAdminThemes(): Promise<Result<ThemeAdmin[]>> {
  return http.get('/themes/admin')
}

function toFormData(p: CreateThemePayload): FormData {
  const form = new FormData()
  form.append('name', p.name)
  form.append('displayName', p.displayName)
  if (p.startDate) form.append('startDate', p.startDate)
  if (p.endDate) form.append('endDate', p.endDate)
  if (p.lunarStart) form.append('lunarStart', p.lunarStart)
  if (p.lunarEnd) form.append('lunarEnd', p.lunarEnd)
  if (p.status) form.append('status', p.status)
  // 文件可选（空壳创建）：仅在非空时追加，避免 FormData 写入 "undefined" 文本字段
  if (p.css) form.append('css', p.css)
  if (p.bannerLight) form.append('bannerLight', p.bannerLight)
  if (p.bannerDark) form.append('bannerDark', p.bannerDark)
  return form
}

/** 创建主题（multipart：css + 亮/暗 banner + 配置字段） */
export function createTheme(p: CreateThemePayload): Promise<Result<ThemeAdmin>> {
  return http.post('/themes/', toFormData(p))
}

/** 更新主题（字段/文件均可选；文件不传 = 保留原文件；date 传 undefined = 清空） */
export function updateTheme(
  name: string,
  p: Partial<Omit<CreateThemePayload, 'name'>> & { css?: File; bannerLight?: File; bannerDark?: File },
): Promise<Result<ThemeAdmin>> {
  const form = new FormData()
  if (p.displayName) form.append('displayName', p.displayName)
  if (p.startDate) form.append('startDate', p.startDate)
  if (p.endDate) form.append('endDate', p.endDate)
  if (p.lunarStart) form.append('lunarStart', p.lunarStart)
  if (p.lunarEnd) form.append('lunarEnd', p.lunarEnd)
  if (p.status) form.append('status', p.status)
  if (p.css) form.append('css', p.css)
  if (p.bannerLight) form.append('bannerLight', p.bannerLight)
  if (p.bannerDark) form.append('bannerDark', p.bannerDark)
  return http.put(`/themes/${name}`, form)
}

/** 删除主题（配置 + 文件登记 + 磁盘文件） */
export function deleteTheme(name: string): Promise<void> {
  return http.delete(`/themes/${name}`)
}

/** 生成预览令牌（未公开主题预览用，10 分钟有效） */
export function createPreviewToken(name: string): Promise<Result<{ token: string }>> {
  return http.post(`/themes/${name}/preview-token`)
}
