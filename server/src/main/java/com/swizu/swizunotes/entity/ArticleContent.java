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

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class ArticleContent {
    // 正文允许为空（空白草稿预留 id 需要空正文）；发布状态下的非空校验在服务层执行
    private String body;

    @Valid
    private List<MediaRef> mediaRefs;
}
