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
import { computed } from 'vue'
import { normalizeContent, renderBody } from '@/utils/articleContent'
import type { ArticleContent } from '@/types/article'

const props = defineProps<{
  content: ArticleContent | string
}>()

const html = computed<string>(() => {
  const { body, mediaRefs } = normalizeContent(props.content)
  return renderBody(body, mediaRefs)
})
</script>

<template>
  <div class="article-content" v-html="html"></div>
</template>

<style scoped>
.article-content {
  line-height: 1.8;
  word-break: break-word;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) {
  margin: 1.5em 0 0.5em;
  line-height: 1.3;
}

.article-content :deep(p) {
  margin: 0.5em 0;
}

.article-content :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

.article-content :deep(a) {
  color: #2563eb;
}

.article-content :deep(code) {
  padding: 0.15em 0.4em;
  background: #f1f5f9;
  border-radius: 3px;
  font-size: 0.9em;
}

.article-content :deep(pre) {
  padding: 1rem;
  overflow-x: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
}

.article-content :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}

.article-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.25em 1em;
  border-left: 4px solid #cbd5e1;
  color: #475569;
}

.article-content :deep(table) {
  border-collapse: collapse;
}

.article-content :deep(th),
.article-content :deep(td) {
  padding: 0.4em 0.8em;
  border: 1px solid #cbd5e1;
}
</style>
