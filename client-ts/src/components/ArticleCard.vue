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
</script>

<template>
  <RouterLink class="card" :to="`/article/${article.id}`">
    <img v-if="article.cover" class="cover" :src="mediaUrl(article.cover)" alt="封面图" />
    <h2 class="title">{{ article.title || `文章 #${article.id}` }}</h2>
    <p v-if="article.summary" class="summary">{{ article.summary }}</p>
    <p class="meta">
      {{ article.status }}
      <template v-if="article.publishTime"> · {{ new Date(article.publishTime).toLocaleString() }}</template>
    </p>
  </RouterLink>
</template>

<style scoped>
.card {
  display: block;
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  color: inherit;
  text-decoration: none;
}

.card:hover {
  border-color: #999;
}

.cover {
  width: 100%;
  height: 10rem;
  object-fit: cover;
  border-radius: 4px;
  margin-bottom: 0.5rem;
}

.title {
  margin: 0 0 0.25rem;
  font-size: 1.1rem;
}

.summary {
  margin: 0 0 0.25rem;
  color: #555;
  font-size: 0.9rem;
}

.meta {
  margin: 0;
  color: #888;
  font-size: 0.85rem;
}
</style>
