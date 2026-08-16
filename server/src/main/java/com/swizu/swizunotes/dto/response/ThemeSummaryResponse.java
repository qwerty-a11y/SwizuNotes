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

package com.swizu.swizunotes.dto.response;

import lombok.Data;

/** 主题摘要（公开列表 / 日期生效主题） */
@Data
public class ThemeSummaryResponse {
    /** 主题名（URL 标识） */
    private String name;
    /** 显示名 */
    private String displayName;

    public ThemeSummaryResponse(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }
}
