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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticle } from '@/api/article'
import { mediaUrl } from '@/api/media'
import { useUserStore } from '@/stores/user'
import ArticleContent from '@/components/ArticleContent.vue'
import { extractHeadings, normalizeContent } from '@/utils/articleContent'
import type { Article } from '@/types/article'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const article = ref<Article | null>(null)
const error = ref('')

const toc = computed(() => {
  if (!article.value) return []
  return extractHeadings(normalizeContent(article.value.content).body)
})

function jumpTo(id: string): void {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

onMounted(async () => {
  try {
    article.value = (await getArticle(Number(route.params.articleId))).data
  } catch (e) {
    error.value = (e as Error).message
  }
})
</script>

<template>
  <div class="page" v-if="article">
    <div
      v-if="article.cover"
      class="page-bg"
      :style="{ '--page-bg-image': `url(${mediaUrl(article.cover)})` }"
      aria-hidden="true"
    ></div>
    <article class="article-card">
      <img v-if="article.cover" class="cover" :src="mediaUrl(article.cover)" alt="封面图" />
      <div v-else class="cover cover-placeholder">SwizuNotes</div>
      <div class="card-body">
        <h1>{{ article.title || `文章 #${article.id}` }}</h1>
        <p class="meta">
          <template v-if="article.publishTime">发布时间：{{ new Date(article.publishTime).toLocaleString() }}</template>
          <template v-if="article.modifyTime"> · 修改时间：{{ new Date(article.modifyTime).toLocaleString() }}</template>
        </p>
        <p v-if="article.summary" class="summary">{{ article.summary }}</p>
        <RouterLink v-if="userStore.isLoggedIn" :to="`/editor/${article.id}`">编辑</RouterLink>
        <ArticleContent :content="article.content" />
      </div>
    </article>
    <aside class="toc" v-if="toc.length">
      <h2 class="toc-title">目录</h2>
      <ul class="toc-list">
        <li v-for="item in toc" :key="item.id" :class="`level-${item.level}`">
          <a :href="`#${item.id}`" @click.prevent="jumpTo(item.id)">{{ item.text }}</a>
        </li>
      </ul>
    </aside>
  </div>
  <p v-else-if="error" class="error">{{ error }}</p>
</template>

<style scoped>
.page {
  position: relative;
  max-width: 78rem;
  margin: 0 auto;
  padding: 2rem 1.5rem 3rem;
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
}

.page-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background-image: linear-gradient(rgba(var(--bg-page-rgb), 0.7), rgba(var(--bg-page-rgb), 0.7)),
    var(--page-bg-image, none);
  background-size: cover;
  background-position: center;
  filter: blur(60px) saturate(140%);
  transform: scale(1.2);
  pointer-events: none;
}

.article-card,
.toc {
  position: relative;
  z-index: 1;
}

.article-card {
  flex: 1;
  min-width: 0;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.cover {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 1;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-muted);
  color: var(--text-faint);
  font-size: 0.9rem;
  letter-spacing: 0.05em;
}

.card-body {
  container-type: inline-size;
  padding: 1.5rem 2rem 2.5rem;
}

.card-body h1 {
  margin: 0 0 0.5rem;
  font-size: 2rem;
  line-height: 1.3;
}

.meta {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.summary {
  color: var(--text-muted);
  font-style: italic;
}

.toc {
  position: sticky;
  top: 4.5rem;
  flex: 0 0 15rem;
  max-height: calc(100svh - 6rem);
  overflow-y: auto;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: var(--shadow-card);
}

.toc-title {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  font-weight: 600;
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.toc-list a {
  display: block;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.4;
  text-decoration: none;
  transition: color 0.2s ease;
}

.toc-list a:hover {
  color: var(--primary);
}

.level-2 {
  padding-left: 1rem;
}

.level-3 {
  padding-left: 2rem;
}

.level-4,
.level-5,
.level-6 {
  padding-left: 3rem;
}

.error {
  color: var(--danger);
  text-align: center;
}

@media (max-width: 1024px) {
  .page {
    flex-direction: column;
  }

  .toc {
    position: static;
    flex: none;
    width: 100%;
    max-height: none;
    order: -1;
  }
}
</style>
