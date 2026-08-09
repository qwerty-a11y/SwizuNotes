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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaRef {
    @NotBlank(message = "媒体ID不能为空")
    @Size(max = 64, message = "媒体ID长度不能超过64")
    private String id;

    @NotNull(message = "媒体类型不能为空")
    private MediaCategory type;

    @NotBlank(message = "别名不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{0,31}$", message = "别名仅限小写字母/数字/下划线/连字符，以字母开头，1-32位")
    private String alias;
}
