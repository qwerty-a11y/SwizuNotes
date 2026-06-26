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

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "articles")
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private Integer authorId;

    @Column(columnDefinition = "jsonb")
    private String content;

    @Column(name = "publish_time", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime publishTime;

    @Column(name = "modify_time", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime modifyTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "article_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ArticleStatus status;
}
