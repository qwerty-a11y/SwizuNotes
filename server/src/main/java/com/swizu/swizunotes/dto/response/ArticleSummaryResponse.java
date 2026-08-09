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

import com.swizu.swizunotes.entity.ArticleStatus;
import lombok.Data;

import java.time.OffsetDateTime;

/** 文章列表项：不含 content 正文，列表页无需下载完整内容 */
@Data
public class ArticleSummaryResponse {
    private Integer id;
    private Integer authorId;
    private String title;
    private String cover;
    private String summary;
    private OffsetDateTime publishTime;
    private OffsetDateTime modifyTime;
    private ArticleStatus status;

    public ArticleSummaryResponse(Integer id, Integer authorId, String title, String cover, String summary, OffsetDateTime publishTime, OffsetDateTime modifyTime, ArticleStatus status) {
        this.id = id;
        this.authorId = authorId;
        this.title = title;
        this.cover = cover;
        this.summary = summary;
        this.publishTime = publishTime;
        this.modifyTime = modifyTime;
        this.status = status;
    }
}
