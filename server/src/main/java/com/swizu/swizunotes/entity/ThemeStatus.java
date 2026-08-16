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

package com.swizu.swizunotes.entity;

/**
 * 主题发布状态（themes.status，VARCHAR 存小写枚举名）：
 *  - unpublished：未发布——完全无法访问，日期设定不生效
 *  - prerelease：预发布——日期设定生效（参与自动切换），生效前对普通用户按未发布处理
 *    （文件不可访问、不进主题选择器），首次生效（日期区间命中）时自动转换为 published
 *  - published：已发布——日期设定生效，普通用户可随时切换（进主题选择器）
 */
public enum ThemeStatus {
    unpublished,
    prerelease,
    published
}
