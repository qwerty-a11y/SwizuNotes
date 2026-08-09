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

import type { MediaCategory } from './media'

export type ArticleStatus = 'draft' | 'published'

export interface MediaRef {
  id: string
  type: MediaCategory
  alias: string
}

export interface ArticleContent {
  body: string
  mediaRefs: MediaRef[]
}

export interface Article {
  id: number
  authorId: number
  title: string
  cover: string | null
  content: ArticleContent
  summary: string | null
  publishTime: string
  modifyTime: string
  status: ArticleStatus
}

export interface EditArticleRequest {
  title: string
  cover?: string
  content: ArticleContent
  summary?: string
  status: ArticleStatus
}

export interface EditArticleResponse {
  id: number
  title: string
  cover: string | null
  summary: string | null
  publishTime: string
  modifyTime: string
  status: ArticleStatus
}

/** 列表项：不含 content 正文（列表页无需下载完整内容） */
export interface ArticleSummary {
  id: number
  authorId: number
  title: string
  cover: string | null
  summary: string | null
  publishTime: string
  modifyTime: string
  status: ArticleStatus
}
