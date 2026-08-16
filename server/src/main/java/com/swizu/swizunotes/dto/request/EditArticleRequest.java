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

package com.swizu.swizunotes.dto.request;

import com.swizu.swizunotes.entity.ArticleContent;
import com.swizu.swizunotes.entity.ArticleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditArticleRequest {
    // 标题允许为空（空白草稿预留 id 需要空标题）；发布状态下的非空校验在服务层执行
    @Size(max = 50, message = "标题长度不能超过50")
    private String title;
    @Size(max = 64, message = "封面图ID无效")
    private String cover;
    @NotNull(message = "正文不能为空")
    @Valid
    private ArticleContent content;
    @Size(max = 50, message = "摘要长度不能超过50")
    private String summary;
    @NotNull(message = "发布状态不能为空")
    private ArticleStatus status;
}
