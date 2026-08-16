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

import http from './http'
import type { Result } from '@/types/api'
import type { Article, ArticleSummary, EditArticleRequest, EditArticleResponse } from '@/types/article'
import type { MediaResponse } from '@/types/media'

export function getArticles(keyword?: string): Promise<Result<ArticleSummary[]>> {
  return http.get('/articles/', { params: keyword ? { keyword } : undefined })
}

export function getArticleMedia(articleId: number): Promise<Result<MediaResponse[]>> {
  return http.get(`/articles/${articleId}/media`)
}

export function getUserArticles(userId: number, keyword?: string): Promise<Result<ArticleSummary[]>> {
  return http.get(`/users/${userId}/articles`, { params: keyword ? { keyword } : undefined })
}

export function getArticle(articleId: number): Promise<Result<Article>> {
  return http.get(`/articles/${articleId}`)
}

export function createArticle(data: EditArticleRequest): Promise<Result<EditArticleResponse>> {
  return http.post('/articles/', data)
}

export function updateArticle(articleId: number, data: EditArticleRequest): Promise<Result<EditArticleResponse>> {
  return http.put(`/articles/${articleId}`, data)
}

export function deleteArticle(articleId: number): Promise<void> {
  return http.delete(`/articles/${articleId}`)
}
