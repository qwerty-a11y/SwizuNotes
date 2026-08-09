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

package com.swizu.swizunotes.controller;

import com.swizu.swizunotes.common.Result;
import com.swizu.swizunotes.dto.request.EditArticleRequest;
import com.swizu.swizunotes.dto.response.ArticleSummaryResponse;
import com.swizu.swizunotes.dto.response.EditArticleResponse;
import com.swizu.swizunotes.entity.Article;
import com.swizu.swizunotes.services.ArticleService;
import com.swizu.swizunotes.services.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
public class ArticlesController {

    @Autowired
    private ArticleService articleService;

    /* 应移动至UserController
    @GetMapping("/author/{userId}/")
    public ResponseEntity<Result<Page<Article>>> getArticles(@PathVariable Integer userId, @AuthenticationPrincipal CustomUserDetails user, Pageable pageable) {
        if (Objects.equals(user.getId(), userId)){
            return ResponseEntity.ok(new Result<>("获取文章成功", articleService.getArticles(userId, pageable)));
        } else {
            return ResponseEntity.ok(new Result<>("获取文章成功", articleService.getPublishedArticles(userId, pageable)));
        }
    }

    */

    @GetMapping("/")
    public ResponseEntity<Result<List<ArticleSummaryResponse>>> getPublishedArticles() {
        return ResponseEntity.ok(new Result<>("获取文章列表成功", articleService.getPublishedArticles()));
    }

    @GetMapping("/{articleId}")
    public ResponseEntity<Result<Article>> getArticle(@PathVariable Integer articleId, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("获取文章成功",
                articleService.getArticle(articleId, userId(user))));
    }

    @PostMapping("/")
    public ResponseEntity<Result<EditArticleResponse>> createArticle(@RequestBody EditArticleRequest article, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("创建文章成功",
                articleService.createArticle(article, userId(user))));
    }

    @PutMapping("/{articleId}")
    public ResponseEntity<Result<EditArticleResponse>> updateArticle(@PathVariable Integer articleId, @RequestBody EditArticleRequest article, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(new Result<>("修改文章成功",
                articleService.updateArticle(articleId, article, userId(user))));
    }

    @DeleteMapping("/{articleId}")
    public ResponseEntity<Void> deleteArticle(@PathVariable Integer articleId, @AuthenticationPrincipal CustomUserDetails user) {
        articleService.deleteArticle(articleId, userId(user));
        return ResponseEntity.noContent().build();
    }

    private Integer userId(CustomUserDetails user) {
        return user == null ? null : user.getId();
    }
}
