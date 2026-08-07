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

import com.swizu.swizunotes.common.exception.ForbiddenException;
import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.common.exception.UnauthorizedException;
import com.swizu.swizunotes.dto.request.EditArticleRequest;
import com.swizu.swizunotes.dto.response.EditArticleResponse;
import com.swizu.swizunotes.entity.Article;
import com.swizu.swizunotes.entity.ArticleStatus;
import com.swizu.swizunotes.repository.ArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class ArticleService {

    @Autowired
    private ArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public Page<Article> getArticles(Integer authorId, Pageable pageable) {
        return articleRepository.findAllByAuthorId(authorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Article> getPublishedArticles(Integer authorId, Pageable pageable) {
        return articleRepository.findAllByAuthorIdAndStatus(authorId, ArticleStatus.published, pageable);
    }

    public Article getArticle(Integer articleId, Integer userId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
        boolean isAuthor = userId != null && Objects.equals(article.getAuthorId(), userId);
        if (!isAuthor && article.getStatus() != ArticleStatus.published) {
            throw new ResourceNotFoundException("文章不存在");
        }
        return article;
    }

    @Transactional
    public EditArticleResponse createArticle(EditArticleRequest request, Integer userId) {
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        Article newArticle = new Article();
        newArticle.setAuthorId(userId);
        newArticle.setContent(request.getContent());
        newArticle.setStatus(request.getStatus());
        OffsetDateTime now = OffsetDateTime.now();
        newArticle.setPublishTime(now);
        newArticle.setModifyTime(now);
        Article savedArticle = articleRepository.save(newArticle);
        return toResponse(savedArticle);
    }

    @Transactional
    public EditArticleResponse updateArticle(Integer articleId, EditArticleRequest request, Integer userId) {
        Article oldArticle = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
        if (userId == null || !Objects.equals(oldArticle.getAuthorId(), userId)) {
            throw new ForbiddenException("无权限修改文章");
        }
        oldArticle.setContent(request.getContent());
        oldArticle.setStatus(request.getStatus());
        oldArticle.setModifyTime(OffsetDateTime.now());
        Article savedArticle = articleRepository.save(oldArticle);
        return toResponse(savedArticle);
    }

    private EditArticleResponse toResponse(Article article) {
        return new EditArticleResponse(
                article.getId(),
                article.getPublishTime(),
                article.getModifyTime(),
                article.getStatus()
        );
    }
}
