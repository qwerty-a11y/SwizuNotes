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
 * API 基址（单一来源）：http.ts 与 theme/index.ts 共用。
 * theme/index.ts 不能 import http.ts（循环初始化：http → router → EditorView → theme），
 * 故抽到独立无副作用模块，两处引用同一值。
 */
export const API_BASE: string = import.meta.env.VITE_API_BASE_URL || '/api/v1'
