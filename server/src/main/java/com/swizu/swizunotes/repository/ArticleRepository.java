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

package com.swizu.swizunotes.repository;

import com.swizu.swizunotes.entity.Article;
import com.swizu.swizunotes.entity.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Integer> {

    Optional<Article> findById(Integer id);

    boolean existsById(Integer id);

    Page<Article> findAllByAuthorId(Integer authorId, Pageable pageable);

    Page<Article> findAllByAuthorIdAndStatus(Integer authorId, ArticleStatus status, Pageable pageable);

    List<Article> findAllByAuthorIdOrderByPublishTimeDesc(Integer authorId);

    List<Article> findAllByAuthorIdAndStatusOrderByPublishTimeDesc(Integer authorId, ArticleStatus status);

    List<Article> findAllByStatusOrderByPublishTimeDesc(ArticleStatus status);

    /** 全局搜索：按标题/摘要 LIKE 过滤已发布文章（keyword 为空时返回全部） */
    @Query("""
            SELECT a FROM Article a
            WHERE a.status = :status
              AND (:keyword = ''
                   OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                   OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
            ORDER BY a.publishTime DESC
            """)
    List<Article> searchByStatus(@Param("status") ArticleStatus status, @Param("keyword") String keyword);

    /** 用户全部文章（含草稿）按关键词过滤 */
    @Query("""
            SELECT a FROM Article a
            WHERE a.authorId = :authorId
              AND (:keyword = ''
                   OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                   OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
            ORDER BY a.publishTime DESC
            """)
    List<Article> searchByAuthorId(@Param("authorId") Integer authorId, @Param("keyword") String keyword);

    /** 用户已发布文章按关键词过滤 */
    @Query("""
            SELECT a FROM Article a
            WHERE a.authorId = :authorId AND a.status = :status
              AND (:keyword = ''
                   OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                   OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
            ORDER BY a.publishTime DESC
            """)
    List<Article> searchByAuthorIdAndStatus(@Param("authorId") Integer authorId,
                                            @Param("status") ArticleStatus status,
                                            @Param("keyword") String keyword);

    Article save(Article article);
}
