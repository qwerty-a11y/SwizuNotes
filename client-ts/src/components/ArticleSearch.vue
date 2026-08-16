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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

/**
 * 导航栏文章搜索：输入关键词后点击右侧搜索按钮（或回车）
 * 跳转到独立搜索页 /search?keyword=xxx（搜索逻辑在 SearchView）。
 * 桌面端内联于导航栏；移动端由 NavBar 控制展开（同组件复用）。
 */
const router = useRouter()

const query = ref('')
const inputEl = ref<HTMLInputElement | null>(null)

function submit(): void {
  const kw = query.value.trim()
  if (!kw) return
  router.push(`/search?keyword=${encodeURIComponent(kw)}`)
  query.value = ''
}

function clear(): void {
  query.value = ''
  inputEl.value?.focus()
}

defineExpose({ focus: () => inputEl.value?.focus() })
</script>

<template>
  <div class="article-search">
    <div class="search-field">
      <input
        ref="inputEl"
        v-model="query"
        type="search"
        placeholder="搜索文章…"
        @keydown.enter.prevent="submit"
      />
      <button v-if="query" class="search-clear" type="button" aria-label="清除搜索" @click="clear">
        <AppIcon name="close" />
      </button>
      <!-- 右侧搜索按钮：点击跳转搜索页 -->
      <button class="search-submit" type="button" aria-label="搜索" title="搜索" @click="submit">
        <AppIcon name="search" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.search-field {
  display: flex;
  align-items: center;
  background: color-mix(in srgb, var(--bg-card) 75%, transparent);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: border-color 0.2s ease;
}

.search-field:focus-within {
  border-color: var(--primary);
}

.search-field input {
  flex: 1;
  min-width: 0;
  padding: 0.45rem 0.6rem 0.45rem 0.75rem;
  background: none;
  border: none;
  outline: none;
  color: var(--text);
  font-size: 0.9rem;
  font-family: inherit;
}

.search-field input::placeholder {
  color: var(--text-faint);
}

/* 隐藏浏览器原生 search 清除按钮（用自定义 .search-clear 代替） */
.search-field input::-webkit-search-cancel-button {
  -webkit-appearance: none;
  appearance: none;
}

.search-clear {
  flex-shrink: 0;
  margin-right: 0.15rem;
  width: 1.4rem;
  height: 1.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: none;
  border: none;
  border-radius: 50%;
  color: var(--text-faint);
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.search-clear:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

.search-clear svg {
  width: 0.85rem;
  height: 0.85rem;
  fill: currentColor;
}

/* 右侧搜索按钮：紧贴输入框右缘（右圆角/左直角），平时与搜索框背景一致，悬停才显主题色 */
.search-submit {
  flex-shrink: 0;
  align-self: stretch;
  width: 2.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 0 9px 9px 0;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.search-submit:hover {
  background: var(--primary);
  color: var(--on-primary);
}

.search-submit svg {
  width: 1.2rem;
  height: 1.2rem;
  fill: currentColor;
}
</style>
