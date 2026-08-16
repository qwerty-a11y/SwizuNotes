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

/**
 * 主题发布状态：
 *  - unpublished：未发布——完全无法访问，日期设定不生效
 *  - prerelease：预发布——日期设定生效（参与自动切换），生效前对普通用户按未发布处理，
 *    首次生效（日期区间命中）时自动转换为 published
 *  - published：已发布——日期设定生效，普通用户可随时切换
 */
export type ThemeStatus = 'unpublished' | 'prerelease' | 'published'

/** 主题摘要（公开列表 / 日期生效主题） */
export interface ThemeSummary {
  /** 主题名（URL 标识） */
  name: string
  /** 显示名 */
  displayName: string
}

/** 主题完整信息（管理页列表，含未发布主题与日期配置） */
export interface ThemeAdmin extends ThemeSummary {
  /** 公历自动切换开始日期（含，可空），yyyy-MM-dd */
  startDate: string | null
  /** 公历自动切换结束日期（含，可空），yyyy-MM-dd */
  endDate: string | null
  /** 农历自动切换开始（M-d 或 闰M-d，如 8-15；每年重复，可空；与 lunarEnd 成对） */
  lunarStart: string | null
  /** 农历自动切换结束（可空；与 lunarStart 成对，同日 = 单日生效） */
  lunarEnd: string | null
  /** 亮色 banner 文件名（如 mid-autumn-light.png；管理页资源预览用，可空） */
  bannerLight: string | null
  /** 暗色 banner 文件名（可空） */
  bannerDark: string | null
  /** 发布状态 */
  status: ThemeStatus
  /** 创建时间 ISO */
  createdAt: string
}

/** 创建主题的表单载荷（multipart） */
export interface CreateThemePayload {
  /** 主题名（小写字母开头，字母/数字/连字符，最长 64） */
  name: string
  displayName: string
  /** 公历开始，yyyy-MM-dd，可空 */
  startDate?: string
  /** 公历结束，yyyy-MM-dd，可空 */
  endDate?: string
  /** 农历开始（M-d 或 闰M-d，如 8-15），可空；与 lunarEnd 成对 */
  lunarStart?: string
  /** 农历结束（可空；与 lunarStart 成对，同日 = 单日生效） */
  lunarEnd?: string
  /** 发布状态（缺省 = unpublished） */
  status?: ThemeStatus
  /** 主题 CSS（含 {{BANNER_LIGHT}} / {{BANNER_DARK}} 占位符）；可选——支持先建空壳主题 */
  css?: File
  /** 亮色 banner 背景图；可选 */
  bannerLight?: File
  /** 暗色 banner 背景图；可选 */
  bannerDark?: File
}
