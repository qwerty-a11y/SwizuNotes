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
import { getArticles } from '@/api/article'
import ArticleCard from '@/components/ArticleCard.vue'
import type { ArticleSummary } from '@/types/article'

const articles = ref<ArticleSummary[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    articles.value = (await getArticles()).data
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="home">
    <h1>SwizuNotes</h1>
    <p v-if="loading">加载中…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <div v-else-if="articles.length" class="list">
      <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
    </div>
    <p v-else class="hint">还没有已发布的文章。</p>
  </section>
</template>

<style scoped>
.home {
  max-width: 48rem;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.home h1 {
  text-align: center;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.hint {
  color: #888;
  text-align: center;
}

.error {
  color: #c0392b;
  text-align: center;
}
</style>
