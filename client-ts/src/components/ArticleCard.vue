<!--
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
 -->
<script setup lang="ts">
import { mediaUrl } from '@/api/media'
import type { ArticleSummary } from '@/types/article'

defineProps<{
  article: ArticleSummary
  to?: string
  /** 紧凑模式：更矮的封面、更小的内边距/字号/间距（个人中心列表用） */
  compact?: boolean
}>()

function formatDate(value: string): string {
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
</script>

<template>
  <RouterLink class="card" :class="{ compact }" :to="to || `/article/${article.id}`">
    <div class="cover">
      <img v-if="article.cover" class="cover-img" :src="mediaUrl(article.cover)" alt="封面图" />
      <div v-else class="cover-placeholder">SwizuNotes</div>
    </div>
    <div class="body">
      <h2 class="title">{{ article.title || `文章 #${article.id}` }}</h2>
      <p v-if="article.summary" class="summary">{{ article.summary }}</p>
      <p v-else class="summary muted">暂无摘要</p>
      <time class="time" :datetime="article.publishTime">
        {{ formatDate(article.publishTime) }}
      </time>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--bg-card);
  color: inherit;
  text-decoration: none;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lift);
}

.cover {
  aspect-ratio: 3 / 1;
  background: var(--bg-muted);
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-faint);
  font-size: 0.9rem;
  letter-spacing: 0.05em;
}

.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 1.1rem 1.25rem;
}

.title {
  margin: 0 0 0.4rem;
  font-size: 1.2rem;
  font-weight: 600;
  line-height: 1.4;
}

.summary {
  margin: 0 0 0.75rem;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.5;
}

.muted {
  color: var(--text-faint);
}

.time {
  margin-top: auto;
  color: var(--text-faint);
  font-size: 0.8rem;
}

/* ===== 紧凑模式（个人中心列表） ===== */

.card.compact {
  border-radius: 12px;
}

.card.compact:hover {
  transform: translateY(-2px);
}

.card.compact .cover {
  aspect-ratio: 16 / 9;
}

.card.compact .body {
  padding: 0.85rem 1rem;
}

.card.compact .title {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
}

.card.compact .summary {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  /* 最多两行，超出省略，避免长摘要撑高卡片 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  /* 右侧为右下角悬浮按钮让位（按钮组宽约 4.75rem，移动端约 5.35rem），
     摘要文本不沾按钮区；日期行靠左且很短，无需让位 */
  padding-right: 5.5rem;
}
</style>
