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
}>()

function formatDate(value: string): string {
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
</script>

<template>
  <RouterLink class="card" :to="`/article/${article.id}`">
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
</style>
