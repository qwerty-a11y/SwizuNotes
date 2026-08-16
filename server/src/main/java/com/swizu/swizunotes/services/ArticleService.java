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

import com.swizu.swizunotes.common.exception.BadRequestException;
import com.swizu.swizunotes.common.exception.ForbiddenException;
import com.swizu.swizunotes.common.exception.InternalException;
import com.swizu.swizunotes.common.exception.ResourceNotFoundException;
import com.swizu.swizunotes.common.exception.UnauthorizedException;
import com.swizu.swizunotes.dto.response.ArticleSummaryResponse;
import com.swizu.swizunotes.dto.request.EditArticleRequest;
import com.swizu.swizunotes.dto.response.EditArticleResponse;
import com.swizu.swizunotes.dto.response.MediaResponse;
import com.swizu.swizunotes.entity.Article;
import com.swizu.swizunotes.entity.ArticleContent;
import com.swizu.swizunotes.entity.ArticleStatus;
import com.swizu.swizunotes.entity.Media;
import com.swizu.swizunotes.repository.ArticleRepository;
import com.swizu.swizunotes.repository.MediaRepository;
import com.swizu.swizunotes.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MediaRepository mediaRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LocalFileStorageService localFileStorageService;

    @Transactional(readOnly = true)
    public Page<Article> getArticles(Integer authorId, Pageable pageable) {
        return articleRepository.findAllByAuthorId(authorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Article> getPublishedArticles(Integer authorId, Pageable pageable) {
        return articleRepository.findAllByAuthorIdAndStatus(authorId, ArticleStatus.published, pageable);
    }

    /** 用户主页文章：本人可见全部（含草稿），他人仅已发布；keyword 非空时按标题/摘要过滤 */
    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> getAuthorArticles(Integer authorId, Integer userId, String keyword) {
        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("资源不存在");
        }
        String kw = escapeLikeKeyword(keyword);
        List<Article> articles;
        if (Objects.equals(authorId, userId)) {
            articles = kw.isEmpty()
                    ? articleRepository.findAllByAuthorIdOrderByPublishTimeDesc(authorId)
                    : articleRepository.searchByAuthorId(authorId, kw);
        } else {
            articles = kw.isEmpty()
                    ? articleRepository.findAllByAuthorIdAndStatusOrderByPublishTimeDesc(authorId, ArticleStatus.published)
                    : articleRepository.searchByAuthorIdAndStatus(authorId, ArticleStatus.published, kw);
        }
        return articles.stream().map(this::toSummary).toList();
    }

    /** 已发布文章列表（全局搜索）：keyword 非空时按标题/摘要过滤 */
    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> getPublishedArticles(String keyword) {
        String kw = escapeLikeKeyword(keyword);
        List<Article> articles = kw.isEmpty()
                ? articleRepository.findAllByStatusOrderByPublishTimeDesc(ArticleStatus.published)
                : articleRepository.searchByStatus(ArticleStatus.published, kw);
        return articles.stream().map(this::toSummary).toList();
    }

    /** 搜索关键词：去首尾空白 + 转义 LIKE 通配符（\ % _），空/全空白返回 ""（表示不过滤） */
    private String escapeLikeKeyword(String keyword) {
        if (keyword == null) return "";
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public Article getArticle(Integer articleId, Integer userId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
        if (getEditPermission(article, userId) == ArticleEditPermission.HIDDEN) {
            throw new ResourceNotFoundException("文章不存在");
        }
        return article;
    }

    public ArticleEditPermission getEditPermission(Integer articleId, Integer userId) {
        return articleRepository.findById(articleId)
                .map(article -> getEditPermission(article, userId))
                .orElse(ArticleEditPermission.HIDDEN);
    }

    /** 文章媒体列表：权限同查看（HIDDEN 404）；VIEW_ONLY 时文章已发布，其媒体全部公开 */
    @Transactional(readOnly = true)
    public List<MediaResponse> getArticleMedia(Integer articleId, Integer userId) {
        switch (getEditPermission(articleId, userId)) {
            case HIDDEN -> throw new ResourceNotFoundException("资源不存在");
            case VIEW_ONLY, EDITABLE -> { }
        }
        return mediaRepository.findByArticleId(articleId).stream()
                .map(media -> new MediaResponse(media.getId(), media.getArticleId(), media.getType(),
                        media.getMimeType(), media.getMetadata(),
                        localFileStorageService.sizeOf(media.getId())))
                .toList();
    }

    private ArticleEditPermission getEditPermission(Article article, Integer userId) {
        if (userId != null && Objects.equals(article.getAuthorId(), userId)) {
            return ArticleEditPermission.EDITABLE;
        }
        if (article.getStatus() == ArticleStatus.published) {
            return ArticleEditPermission.VIEW_ONLY;
        }
        return ArticleEditPermission.HIDDEN;
    }

    @Transactional
    public EditArticleResponse createArticle(EditArticleRequest request, Integer userId) {
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        // 空草稿 = 预留 id：每用户最多持有一个空白草稿，已有则复用（避免创建多篇空文章）
        boolean blankRequest = request.getStatus() == ArticleStatus.draft && isBlankRequest(request);
        log.debug("createArticle userId={} status={} blankRequest={}", userId, request.getStatus(), blankRequest);
        if (blankRequest) {
            Article existing = articleRepository.findAllByAuthorIdOrderByPublishTimeDesc(userId).stream()
                    .filter(this::isBlankDraft)
                    .findFirst()
                    .orElse(null);
            log.debug("blank-draft reuse: {}", existing == null ? "none" : "id=" + existing.getId() + " status=" + existing.getStatus());
            if (existing != null) {
                // 清理该用户其他"从未保存过"（modifyTime == publishTime）的空白草稿，只保留一篇预留
                articleRepository.findAllByAuthorIdOrderByPublishTimeDesc(userId).stream()
                        .filter(this::isBlankDraft)
                        .filter(a -> !a.getId().equals(existing.getId()))
                        .filter(a -> a.getPublishTime() != null && a.getPublishTime().equals(a.getModifyTime()))
                        .forEach(a -> articleRepository.delete(a));
                return toResponse(existing);
            }
        }
        if (request.getStatus() == ArticleStatus.published) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BadRequestException("发布文章需填写标题");
            }
            if (isBlankContent(request.getContent())) {
                throw new BadRequestException("发布文章需填写正文");
            }
        }
        Article newArticle = new Article();
        newArticle.setAuthorId(userId);
        newArticle.setTitle(request.getTitle());
        newArticle.setCover(request.getCover());
        newArticle.setContent(request.getContent());
        newArticle.setSummary(request.getSummary());
        newArticle.setStatus(request.getStatus());
        OffsetDateTime now = OffsetDateTime.now();
        newArticle.setPublishTime(now);
        newArticle.setModifyTime(now);
        Article savedArticle = articleRepository.save(newArticle);
        return toResponse(savedArticle);
    }

    /** 请求是否为空白草稿（标题/摘要/封面/正文/引用全空） */
    private boolean isBlankRequest(EditArticleRequest request) {
        return (request.getTitle() == null || request.getTitle().isBlank())
                && (request.getSummary() == null || request.getSummary().isBlank())
                && (request.getCover() == null || request.getCover().isBlank())
                && isBlankContent(request.getContent());
    }

    /** 文章是否为空白草稿（草稿状态 + 内容全空） */
    private boolean isBlankDraft(Article article) {
        return article.getStatus() == ArticleStatus.draft
                && (article.getTitle() == null || article.getTitle().isBlank())
                && (article.getSummary() == null || article.getSummary().isBlank())
                && (article.getCover() == null || article.getCover().isBlank())
                && isBlankContent(article.getContent());
    }

    private boolean isBlankContent(ArticleContent content) {
        return content == null
                || (content.getBody() == null || content.getBody().isBlank())
                && (content.getMediaRefs() == null || content.getMediaRefs().isEmpty());
    }

    @Transactional
    public EditArticleResponse updateArticle(Integer articleId, EditArticleRequest request, Integer userId) {
        Article oldArticle = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在"));
        if (userId == null || !Objects.equals(oldArticle.getAuthorId(), userId)) {
            if (oldArticle.getStatus() == ArticleStatus.draft) {
                throw new ResourceNotFoundException("文章不存在");
            }
            throw new ForbiddenException("无权限修改文章");
        }

        if (request.getStatus() == ArticleStatus.published) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BadRequestException("发布文章需填写标题");
            }
            if (isBlankContent(request.getContent())) {
                throw new BadRequestException("发布文章需填写正文");
            }
        }
        oldArticle.setTitle(request.getTitle());
        oldArticle.setCover(request.getCover());
        oldArticle.setContent(request.getContent());
        oldArticle.setSummary(request.getSummary());
        oldArticle.setStatus(request.getStatus());
        oldArticle.setModifyTime(OffsetDateTime.now());
        Article savedArticle = articleRepository.save(oldArticle);
        return toResponse(savedArticle);
    }

    @Transactional
    public void deleteArticle(Integer articleId, Integer userId) {
        switch (getEditPermission(articleId, userId)) {
            case HIDDEN -> throw new ResourceNotFoundException("文章不存在");
            case VIEW_ONLY -> throw new ForbiddenException("无权限删除文章");
            case EDITABLE -> { }
        }
        // 级联删除文章全部媒体（含封面图与音频封面等——所有媒体记录都挂在文章下）：
        // 先删 DB 记录，再删磁盘文件
        List<Media> mediaList = mediaRepository.findByArticleId(articleId);
        mediaRepository.deleteByArticleId(articleId);
        articleRepository.deleteById(articleId);
        for (Media media : mediaList) {
            try {
                localFileStorageService.delete(media.getId());
            } catch (InternalException e) {
                // 单个文件删除失败不阻断其余（DB 已删保持提交；残留文件为孤儿文件，
                // 由人工清理——避免"DB 回滚 + 文件已删"导致媒体记录指向缺失文件）
                log.warn("deleteArticle: 文件删除失败 mediaId={}", media.getId(), e);
            }
        }
    }

    private ArticleSummaryResponse toSummary(Article article) {
        return new ArticleSummaryResponse(
                article.getId(),
                article.getAuthorId(),
                article.getTitle(),
                article.getCover(),
                article.getSummary(),
                article.getPublishTime(),
                article.getModifyTime(),
                article.getStatus()
        );
    }

    private EditArticleResponse toResponse(Article article) {
        return new EditArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getCover(),
                article.getSummary(),
                article.getPublishTime(),
                article.getModifyTime(),
                article.getStatus()
        );
    }
}
