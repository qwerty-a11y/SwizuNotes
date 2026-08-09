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
import { createArticle, getArticle, updateArticle } from '@/api/article'
import { mediaUrl } from '@/api/media'
import { normalizeContent, uniqueAlias } from '@/utils/articleContent'
import ImageUploader from '@/components/ImageUploader.vue'
import type { ArticleContent, ArticleStatus, MediaRef } from '@/types/article'
import type { MediaResponse } from '@/types/media'

const route = useRoute()
const router = useRouter()

const articleId = Number(route.params.articleId) || 0
const title = ref('')
const cover = ref('')
const summary = ref('')
const content = ref<ArticleContent>({ body: '', mediaRefs: [] })
const status = ref<ArticleStatus>('draft')
const error = ref('')
const loading = ref(false)

onMounted(async () => {
  if (!articleId) return
  try {
    const article = (await getArticle(articleId)).data
    title.value = article.title || ''
    cover.value = article.cover || ''
    summary.value = article.summary || ''
    content.value = normalizeContent(article.content)
    status.value = article.status
  } catch (e) {
    error.value = (e as Error).message
  }
})

function onCoverUploaded(media: MediaResponse): void {
  cover.value = media.id
}

function onImageUploaded(media: MediaResponse): void {
  const alias = uniqueAlias(content.value.mediaRefs)
  content.value.mediaRefs.push({ id: media.id, type: media.type, alias } satisfies MediaRef)
  content.value.body += `\n![图片：${alias}](media://${alias})\n`
}

async function handleSave(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const result = articleId
      ? (await updateArticle(articleId, {
          title: title.value,
          cover: cover.value,
          content: { body: content.value.body, mediaRefs: content.value.mediaRefs },
          summary: summary.value,
          status: status.value,
        })).data
      : (await createArticle({
          title: title.value,
          cover: cover.value,
          content: { body: content.value.body, mediaRefs: content.value.mediaRefs },
          summary: summary.value,
          status: status.value,
        })).data
    router.push(`/article/${result.id}`)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="editor">
    <h1>{{ articleId ? '编辑文章' : '写文章' }}</h1>
    <label>
      标题
      <input v-model="title" type="text" maxlength="50" required />
    </label>
    <label>
      摘要
      <textarea v-model="summary" rows="2" maxlength="50" placeholder="选填，列表页展示，最多 50 字"></textarea>
    </label>
    <div class="cover">
      <p>封面图（选填）</p>
      <ImageUploader v-if="articleId" :article-id="articleId" @uploaded="onCoverUploaded" />
      <img v-if="cover" class="cover-preview" :src="mediaUrl(cover)" alt="封面图" />
      <button v-if="cover" class="cover-clear" type="button" @click="cover = ''">移除封面图</button>
      <p v-else-if="!articleId" class="hint">保存文章后即可上传封面图</p>
    </div>
    <label>
      状态
      <select v-model="status">
        <option value="draft">草稿</option>
        <option value="published">发布</option>
      </select>
    </label>
    <label>
      正文（Markdown）
      <textarea v-model="content.body" rows="16" required></textarea>
    </label>
    <p class="hint">正文中引用图片：`![描述](media://别名)`，上传图片后自动插入正文和媒体清单</p>
    <div class="media-refs" v-if="content.mediaRefs.length">
      <p>媒体清单</p>
      <ul>
        <li v-for="ref in content.mediaRefs" :key="ref.alias">{{ ref.alias }}（{{ ref.type }}）</li>
      </ul>
    </div>
    <ImageUploader v-if="articleId" :article-id="articleId" @uploaded="onImageUploaded" />
    <p v-if="error" class="error">{{ error }}</p>
    <button @click="handleSave" :disabled="loading">{{ loading ? '保存中…' : '保存' }}</button>
  </section>
</template>

<style scoped>
.editor {
  max-width: 48rem;
  margin: 0 auto;
  padding: 2rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.editor label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.editor textarea,
.editor select,
.editor input {
  padding: 0.5rem;
  font-family: inherit;
}

.cover-preview {
  max-width: 16rem;
  max-height: 9rem;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.cover-clear {
  align-self: flex-start;
}

.media-refs ul {
  margin: 0;
  padding-left: 1.25rem;
  color: #555;
}

.hint {
  color: #888;
  font-size: 0.9rem;
}

.editor button {
  align-self: flex-start;
  padding: 0.5rem 1.5rem;
  cursor: pointer;
}

.error {
  color: #c0392b;
}
</style>
