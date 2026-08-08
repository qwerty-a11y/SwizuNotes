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

package com.swizu.swizunotes.services;

public enum ArticleEditPermission {
    /** 作者本人，可编辑 */
    EDITABLE,
    /** 可查看但不可编辑（已发布文章），存在性不隐藏 */
    VIEW_ONLY,
    /** 不可查看也不可编辑（不存在或他人草稿），存在性隐藏 */
    HIDDEN
}
