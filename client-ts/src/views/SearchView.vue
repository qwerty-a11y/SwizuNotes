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
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticles } from '@/api/article'
import ArticleCard from '@/components/ArticleCard.vue'
import { toast } from '@/utils/toast'
import type { ArticleSummary } from '@/types/article'

const route = useRoute()
const router = useRouter()

/** 输入框内容（v-model，编辑时不影响已展示的结果） */
const keyword = ref('')
/** 当前实际搜索的关键词（跟随 URL），提示文本/结果以此为准 */
const current = ref('')
const results = ref<ArticleSummary[]>([])
const loading = ref(true)

/** 读取 URL 关键词（/search?keyword=xxx）并请求后端搜索 */
let loadSeq = 0
async function load(): Promise<void> {
  // vue-router query 值可能是 string | string[] | null：重复参数时取第一个，避免 TypeError
  const raw = route.query.keyword
  const q = Array.isArray(raw) ? raw[0] ?? '' : raw ?? ''
  keyword.value = q
  current.value = q
  loading.value = true
  if (!q.trim()) {
    results.value = []
    loading.value = false
    return
  }
  const seq = ++loadSeq
  try {
    const data = (await getArticles(q.trim())).data
    // 仅采纳最新一次请求的结果（快速切换关键词时旧响应后到不覆盖新结果）
    if (seq === loadSeq) {
      results.value = data
    }
  } catch (e) {
    if (seq === loadSeq) {
      toast.error((e as Error).message)
    }
  } finally {
    if (seq === loadSeq) {
      loading.value = false
    }
  }
}

/** 搜索框内再次搜索：URL 变化（router.push）→ watch 触发重新搜索；相同关键词直接重搜 */
function onSearch(): void {
  const kw = keyword.value.trim()
  if (!kw) return
  if (kw === current.value) {
    void load()
  } else {
    router.push({ path: '/search', query: { keyword: kw } })
  }
}

/** URL 关键词变化（router.push / 前进后退 / 直接导航）时重新搜索 */
watch(() => route.query.keyword, () => void load())

onMounted(load)
</script>

<template>
  <div class="search-page">
    <div class="search-head">
      <h1 class="search-title">搜索</h1>
      <form class="search-form" @submit.prevent="onSearch">
        <input v-model="keyword" type="search" placeholder="搜索文章标题或摘要…" />
        <button type="submit">搜索</button>
      </form>
    </div>

    <p v-if="loading" class="state">搜索中…</p>
    <template v-else-if="current.trim()">
      <div v-if="results.length" class="grid">
        <ArticleCard v-for="article in results" :key="article.id" :article="article" />
      </div>
      <p v-else class="state hint">没有找到与「{{ current.trim() }}」相关的文章。</p>
    </template>
    <p v-else class="state hint">输入关键词开始搜索。</p>
  </div>
</template>

<style scoped>
.search-page {
  max-width: 64rem;
  margin: 0 auto;
  padding: 2rem 1.5rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.search-head {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.search-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.search-form {
  display: flex;
  gap: 0.5rem;
}

.search-form input {
  flex: 1;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  background: color-mix(in srgb, var(--bg-card) 75%, transparent);
  border: 1px solid var(--border);
  border-radius: 10px;
  color: var(--text);
  font-size: 0.95rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s ease;
}

.search-form input:focus {
  border-color: var(--primary);
}

.search-form input::placeholder {
  color: var(--text-faint);
}

.search-form button {
  flex-shrink: 0;
  padding: 0.55rem 1.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--on-primary);
  background: var(--primary);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.search-form button:hover {
  background: var(--primary-hover);
}

.grid {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.state {
  color: var(--text-faint);
  text-align: center;
  padding: 3rem 0;
}

.error {
  color: var(--danger);
}

.hint {
  padding: 1.5rem 0;
}
</style>
