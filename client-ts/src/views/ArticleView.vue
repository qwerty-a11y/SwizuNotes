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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticle } from '@/api/article'
import { mediaUrl } from '@/api/media'
import { useUserStore } from '@/stores/user'
import ArticleContent from '@/components/ArticleContent.vue'
import type { Article } from '@/types/article'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const article = ref<Article | null>(null)
const error = ref('')

onMounted(async () => {
  try {
    article.value = (await getArticle(Number(route.params.articleId))).data
  } catch (e) {
    error.value = (e as Error).message
  }
})
</script>

<template>
  <article class="article" v-if="article">
    <header>
      <img v-if="article.cover" class="cover" :src="mediaUrl(article.cover)" alt="封面图" />
      <h1>{{ article.title || `文章 #${article.id}` }}</h1>
      <p class="meta">
        状态：{{ article.status }}
        <template v-if="article.publishTime"> · 发布时间：{{ new Date(article.publishTime).toLocaleString() }}</template>
        <template v-if="article.modifyTime"> · 修改时间：{{ new Date(article.modifyTime).toLocaleString() }}</template>
      </p>
      <p v-if="article.summary" class="summary">{{ article.summary }}</p>
      <RouterLink v-if="userStore.isLoggedIn" :to="`/editor/${article.id}`">编辑</RouterLink>
    </header>
    <ArticleContent :content="article.content" />
  </article>
  <p v-else-if="error" class="error">{{ error }}</p>
</template>

<style scoped>
.article {
  max-width: 48rem;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.cover {
  display: block;
  max-width: 100%;
  max-height: 20rem;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.meta {
  color: #888;
  font-size: 0.9rem;
}

.summary {
  color: #555;
  font-style: italic;
}

.error {
  color: #c0392b;
  text-align: center;
}
</style>
