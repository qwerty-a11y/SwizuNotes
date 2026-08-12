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
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { DropdownToolbar, MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { createArticle, getArticle, updateArticle } from '@/api/article'
import { API_BASE, TOKEN_KEY } from '@/api/http'
import { mediaUrl, uploadMedia } from '@/api/media'
import { mediaCardHtml, normalizeContent, uniqueAlias } from '@/utils/articleContent'
import { useMediaCards } from '@/composables/useMediaCards'
import MediaUploadDialog from '@/components/MediaUploadDialog.vue'
import type { ArticleContent, ArticleStatus, MediaRef } from '@/types/article'
import type { MediaCategory, MediaResponse } from '@/types/media'

const route = useRoute()
const router = useRouter()

const routeArticleId = Number(route.params.articleId) || 0
/** 新文章进入编辑页时自动创建草稿，保证随时可上传文件 */
const articleId = ref(routeArticleId)
const title = ref('')
const cover = ref('')
const summary = ref('')
const content = ref<ArticleContent>({ body: '', mediaRefs: [] })
const status = ref<ArticleStatus>('draft')
const originalStatus = ref<ArticleStatus>('draft')
const error = ref('')
const loading = ref(false)

const initialTitle = ref('')
const initialCover = ref('')
const initialSummary = ref('')
const initialBody = ref('')
const initialRefs = ref('')

/** 快照当前表单，作为"未保存更改"的基准 */
function snapshotForm(): void {
  initialTitle.value = title.value
  initialCover.value = cover.value
  initialSummary.value = summary.value
  initialBody.value = content.value.body
  initialRefs.value = JSON.stringify(content.value.mediaRefs)
}

function hasUnsavedChanges(): boolean {
  return (
    title.value !== initialTitle.value ||
    cover.value !== initialCover.value ||
    summary.value !== initialSummary.value ||
    content.value.body !== initialBody.value ||
    JSON.stringify(content.value.mediaRefs) !== initialRefs.value
  )
}

function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (hasUnsavedChanges()) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
})

onBeforeRouteLeave(() => {
  if (hasUnsavedChanges()) {
    if (!window.confirm('有未保存的更改，确定离开吗？')) {
      return false
    }
  }
})
const editorRef = ref<{ insert: (text: string) => void } | null>(null)
const editorWrap = ref<HTMLElement | null>(null)
const mediaCards = useMediaCards()
let fillTimer: ReturnType<typeof setTimeout> | null = null
let previewObserver: MutationObserver | null = null

const dialogType = ref<MediaCategory | null>(null)
const dialogFile = ref<File | null>(null)
const dropdownVisible = ref(false)

const uploadTrigger = h(
  'svg',
  { viewBox: '0 0 24 24', style: 'width:1.1rem;height:1.1rem;fill:currentColor', 'aria-hidden': 'true' },
  [h('path', { d: 'M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z' })],
)

/** 自定义工具栏：上传媒体文件悬停菜单（toolbars 中以数字 0 引用，visible 受控 + computed 响应式重建） */
const defToolbars = computed(() => [
  h(
    DropdownToolbar,
    {
      title: '上传媒体文件',
      visible: dropdownVisible.value,
      onChange: (v: boolean) => {
        dropdownVisible.value = v
      },
    },
    {
      trigger: () => h('span', { class: 'upload-trigger' }, [uploadTrigger]),
      overlay: () =>
        h(
          'ul',
          { class: 'md-editor-menu', role: 'menu' },
          [
            ['图片', 'image'],
            ['音频', 'audio'],
            ['视频', 'video'],
            ['文件', 'file'],
          ].map(([label, type]) =>
            h('li', { class: 'md-editor-menu-item', role: 'menuitem', tabindex: '0', onClick: () => openUploadDialog(type as MediaCategory) }, label),
          ),
        ),
    },
  ),
])

const mediaRefMap = computed(() => new Map(content.value.mediaRefs.map((ref) => [ref.alias, ref])))

function openUploadDialog(type: MediaCategory, file?: File | null): void {
  dropdownVisible.value = false
  dialogType.value = type
  dialogFile.value = file ?? null
}

function onMediaUploaded(media: MediaResponse): void {
  const alias = uniqueAlias(content.value.mediaRefs)
  content.value.mediaRefs.push({ id: media.id, type: media.type, alias } satisfies MediaRef)
  let label = ''
  try {
    label = JSON.parse(media.metadata).name || ''
  } catch {
    // 名称解析失败则用 alias
  }
  const md =
    media.type === 'image' ? `![${label}](media://${alias})` : `[${label}](media://${alias})`
  editorRef.value?.insert(md)
  dialogType.value = null
}

const IMAGE_EXTS = ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'svg', 'avif']
const AUDIO_EXTS = ['mp3', 'flac', 'wav', 'ogg', 'm4a', 'aac', 'opus']
const VIDEO_EXTS = ['mp4', 'webm', 'mkv', 'avi', 'mov', 'm4v', 'ts']

function extToCategory(fileName: string): MediaCategory {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
  if (IMAGE_EXTS.includes(ext)) return 'image'
  if (AUDIO_EXTS.includes(ext)) return 'audio'
  if (VIDEO_EXTS.includes(ext)) return 'video'
  return 'file'
}

function onEditorDrop(event: DragEvent): void {
  event.preventDefault()
  const f = event.dataTransfer?.files?.[0]
  if (!f) return
  openUploadDialog(extToCategory(f.name), f)
}

/** 预览中媒体 URL（草稿媒体附加 token） */
function toMediaUrl(id: string): string {
  const token = localStorage.getItem(TOKEN_KEY)
  return token
    ? `${API_BASE}/media/${id}?token=${encodeURIComponent(token)}`
    : `${API_BASE}/media/${id}`
}

onMounted(async () => {
  if (routeArticleId) {
    try {
      const article = (await getArticle(routeArticleId)).data
      title.value = article.title || ''
      cover.value = article.cover || ''
      summary.value = article.summary || ''
      content.value = normalizeContent(article.content)
      status.value = article.status
      originalStatus.value = article.status
      snapshotForm()
    } catch (e) {
      error.value = (e as Error).message
    }
  } else {
    await ensureDraft()
  }
})

/** 新文章：立即创建空草稿，解锁媒体上传 */
async function ensureDraft(): Promise<void> {
  if (articleId.value) return
  try {
    const result = (await createArticle({
      title: '',
      cover: '',
      content: { body: '', mediaRefs: [] },
      summary: '',
      status: 'draft',
    })).data
    articleId.value = result.id
    snapshotForm()
  } catch (e) {
    error.value = `自动创建草稿失败：${(e as Error).message}`
  }
}

function onCoverUploaded(media: MediaResponse): void {
  cover.value = media.id
}

function uploadCoverFile(file: File): void {
  if (!articleId.value) {
    error.value = '请先保存文章，再上传封面图'
    return
  }
  if (!file.type.startsWith('image/')) {
    error.value = '封面图必须是图片文件'
    return
  }
  uploadMedia(file, articleId.value, 'image')
    .then((result) => onCoverUploaded(result.data))
    .catch((e) => {
      error.value = (e as Error).message
    })
}

function onCoverFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) uploadCoverFile(file)
}

function onCoverDrop(event: DragEvent): void {
  event.preventDefault()
  coverDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) uploadCoverFile(file)
}

const coverDragOver = ref(false)
const coverWrap = ref<HTMLElement | null>(null)

function onCoverDragEnter(): void {
  coverDragOver.value = true
}

function onCoverDragLeave(event: DragEvent): void {
  if (!coverWrap.value?.contains(event.relatedTarget as Node)) {
    coverDragOver.value = false
  }
}

async function onUploadImg(files: File[], callback: (urls: string[], texts?: string[]) => void): Promise<void> {
  if (!articleId.value) {
    error.value = '请先保存文章，再上传图片'
    callback([])
    return
  }
  const texts: string[] = []
  for (const file of files) {
    try {
      const media = (await uploadMedia(file, articleId.value, 'image')).data
      const alias = uniqueAlias(content.value.mediaRefs)
      content.value.mediaRefs.push({ id: media.id, type: media.type, alias } satisfies MediaRef)
      texts.push(`![图片：${alias}](media://${alias})`)
    } catch (e) {
      error.value = (e as Error).message
    }
  }
  callback([], texts)
}

/** 预览渲染：media:// 图片替换为可显示 URL，非图片链接渲染为与文章页一致的多媒体卡片 */
function previewSanitize(html: string): string {
  return html
    .replace(/<img src="media:\/\/([a-z0-9_-]+)"([^>]*)>/g, (match, alias: string) => {
      const ref = mediaRefMap.value.get(alias)
      return ref ? `<img src="${toMediaUrl(ref.id)}" alt="">` : match
    })
    .replace(/<a href="media:\/\/([a-z0-9_-]+)"[^>]*>([\s\S]*?)<\/a>/g, (match, alias: string) => {
      const ref = mediaRefMap.value.get(alias)
      if (!ref) return match
      return mediaCardHtml(ref, toMediaUrl(ref.id))
    })
}

async function fillPreview(): Promise<void> {
  const preview = editorWrap.value?.querySelector<HTMLElement>('.md-editor-preview')
  if (preview) {
    await mediaCards.fill(preview)
  }
}

function scheduleFillPreview(): void {
  if (fillTimer) clearTimeout(fillTimer)
  fillTimer = setTimeout(() => {
    void fillPreview()
  }, 200)
}

/** 全屏状态同步：编辑器全屏时提升卡片 z-index，避免被导航栏覆盖 */
function syncFullscreen(): void {
  const editor = editorWrap.value?.querySelector<HTMLElement>('.md-editor')
  const isFullscreen = editor?.classList.contains('md-editor-fullscreen') ?? false
  editorWrap.value?.closest('.editor')?.classList.toggle('editor-fullscreen-active', isFullscreen)
}

onMounted(() => {
  previewObserver = new MutationObserver((mutations) => {
    let needsFill = false
    for (const mutation of mutations) {
      if (mutation.type === 'attributes') {
        // class 变化（播放器 UI 状态、全屏标记）只同步全屏，不触发重填
        syncFullscreen()
        continue
      }
      const target = mutation.target as HTMLElement
      // 播放器自身产生的 DOM 变化（ArtPlayer 渲染、封面填充等）不触发重填
      if (target.closest?.('.media-card, .media-video')) continue
      needsFill = true
    }
    if (needsFill) scheduleFillPreview()
  })
  if (editorWrap.value) {
    previewObserver.observe(editorWrap.value, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class'],
    })
  }
  void nextTick(fillPreview)
})

onBeforeUnmount(() => {
  previewObserver?.disconnect()
  if (fillTimer) clearTimeout(fillTimer)
  mediaCards.dispose()
})

async function saveWithStatus(saveStatus: ArticleStatus): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    let result
    if (articleId.value) {
      result = (await updateArticle(articleId.value, {
        title: title.value,
        cover: cover.value,
        content: { body: content.value.body, mediaRefs: content.value.mediaRefs },
        summary: summary.value,
        status: saveStatus,
      })).data
    } else {
      // 自动创建草稿失败时的兜底：保存时创建
      result = (await createArticle({
        title: title.value,
        cover: cover.value,
        content: { body: content.value.body, mediaRefs: content.value.mediaRefs },
        summary: summary.value,
        status: saveStatus,
      })).data
      articleId.value = result.id
    }
    originalStatus.value = saveStatus
    snapshotForm()
    router.push(`/article/${result.id}`)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

/** 保存：新文章存草稿，编辑时保持原有状态 */
function handleSave(): void {
  void saveWithStatus(articleId.value ? originalStatus.value : 'draft')
}

/** 发布/撤回：切换 draft <-> published */
function handleTogglePublish(): void {
  void saveWithStatus(status.value === 'published' ? 'draft' : 'published')
}
</script>

<template>
  <div class="editor-page">
    <Transition name="bg">
      <div
        v-if="cover"
        :key="cover"
        class="editor-bg"
        :style="{ '--page-bg-image': `url(${mediaUrl(cover)})` }"
        aria-hidden="true"
      ></div>
    </Transition>
    <section class="editor">
      <h1 class="title">编辑文章</h1>

    <div class="field">
      <label for="article-title">标题</label>
      <input id="article-title" v-model="title" type="text" maxlength="50" required placeholder="请输入文章标题（最多 50 字）" />
    </div>

    <div class="field">
      <label for="article-summary">摘要</label>
      <textarea
        id="article-summary"
        v-model="summary"
        rows="3"
        maxlength="50"
        placeholder="选填，列表页展示，最多 50 字"
      ></textarea>
    </div>

    <div class="field">
      <label>封面图（选填）</label>
      <div
        ref="coverWrap"
        class="cover-wrap"
        @dragenter.prevent="onCoverDragEnter"
        @dragover.prevent
        @dragleave="onCoverDragLeave"
        @drop="onCoverDrop"
      >
        <label v-if="!cover" class="cover-dropzone">
          上传封面图
          <span>点击选择或拖拽图片到此处</span>
          <input type="file" accept="image/*" @change="onCoverFileChange" />
        </label>
        <div v-else class="cover-preview-wrap">
          <img class="cover-preview" :src="mediaUrl(cover)" alt="封面图" />
          <div class="cover-overlay">
            <label class="cover-action">
              更换图片
              <input type="file" accept="image/*" @change="onCoverFileChange" />
            </label>
            <button class="cover-action" type="button" @click="cover = ''">移除封面</button>
          </div>
        </div>
        <div v-if="coverDragOver" class="cover-drag-mask">松开以{{ cover ? '更换' : '上传' }}封面图</div>
      </div>
      <p v-if="!articleId" class="hint">保存文章后即可上传封面图</p>
    </div>

    <div class="field">
      <label>正文（Markdown）</label>
      <div
        ref="editorWrap"
        class="editor-wrap"
        @click="mediaCards.handleClick"
        @dragover.prevent
        @drop="onEditorDrop"
      >
        <MdEditor
          ref="editorRef"
          v-model="content.body"
          :on-upload-img="onUploadImg"
          :sanitize="previewSanitize"
          :def-toolbars="defToolbars"
          style="height: 520px"
          language="zh-CN"
          :toolbars="['bold', 'italic', 'strikeThrough', 'sub', 'sup', 'quote', 'orderedList', 'unorderedList', 'task', 'link', 'code', 'codeRow', 'table', 0, 'divider', 'preview', 'fullscreen', 'catalog']"
        />
      </div>
      <p class="hint">支持图片/音频/视频/文件：工具栏悬停上传或直接拖拽文件到编辑器，按扩展名自动归类；音频自动提取 ID3 封面、标题与时长</p>
    </div>

    <MediaUploadDialog
      v-if="dialogType"
      :type="dialogType"
      :article-id="articleId"
      :file="dialogFile"
      @close="dialogType = null"
      @uploaded="onMediaUploaded"
    />

    <div v-if="content.mediaRefs.length" class="media-refs">
      <span class="media-refs-title">媒体清单：</span>
      <span v-for="ref in content.mediaRefs" :key="ref.alias" class="media-ref-tag">{{ ref.alias }}（{{ ref.type }}）</span>
    </div>

    <div class="footer">
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn-ghost" @click="handleSave" :disabled="loading">{{ loading ? '保存中…' : '保存' }}</button>
      <button class="btn-primary" @click="handleTogglePublish" :disabled="loading">
        {{ loading ? '处理中…' : status === 'published' ? '撤回' : '发布' }}
      </button>
    </div>
    </section>
  </div>
</template>

<style scoped>
.editor-page {
  position: relative;
}

.editor-bg {
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

.bg-enter-active,
.bg-leave-active {
  transition: opacity 0.5s ease;
}

.bg-enter-from,
.bg-leave-to {
  opacity: 0;
}

.editor {
  position: relative;
  z-index: 1;
  max-width: 64rem;
  margin: 2rem auto 3rem;
  padding: 2rem 2.5rem 2.5rem;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

:deep(.md-editor) {
  --md-color: var(--text);
  --md-hover-color: var(--text-strong);
  --md-bk-color: transparent;
  --md-bk-color-outstand: transparent;
  --md-bk-hover-color: transparent;
  --md-border-color: var(--border);
  --md-border-hover-color: var(--border-strong);
  --md-border-active-color: var(--primary);
}

:deep(.md-editor.md-editor-fullscreen) {
  --md-bk-color: #fff;
  --md-bk-color-outstand: #f2f2f2;
  --md-bk-hover-color: #f5f7fa;
  background-color: #fff;
}

.editor-fullscreen-active {
  z-index: 200;
}

:deep(.upload-trigger) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

:deep(.md-editor-preview) {
  container-type: inline-size;
}

:deep(.md-editor-preview img) {
  max-width: 100%;
  max-height: calc(100cqw * 9 / 16);
}

:deep(.md-editor-preview .media-card),
:deep(.md-editor-preview .media-video) {
  margin: 0.75em 0;
}

:deep(.md-editor-preview .media-card) {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
}

:deep(.md-editor-preview .media-card-cover) {
  flex-shrink: 0;
  width: 4.5rem;
  height: 4.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-muted);
  border-radius: 8px;
  overflow: hidden;
}

:deep(.md-editor-preview .media-card-cover-img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}

:deep(.md-editor-preview .media-card-type) {
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}

:deep(.md-editor-preview .media-card-body) {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

:deep(.md-editor-preview .media-card-name) {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.md-editor-preview .media-card-size) {
  font-size: 0.75rem;
  color: var(--text-faint);
}

:deep(.md-editor-preview .media-icon-btn) {
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 6px;
}

:deep(.md-editor-preview .media-icon-btn svg) {
  width: 1.1rem;
  height: 1.1rem;
  fill: currentColor;
}

:deep(.md-editor-preview .audio-player) {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

:deep(.md-editor-preview .audio-toggle) {
  flex-shrink: 0;
  width: 2.2rem;
  height: 2.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: none;
  color: var(--primary);
  cursor: pointer;
  padding: 0;
}

:deep(.md-editor-preview .audio-toggle svg) {
  width: 1.1rem;
  height: 1.1rem;
  fill: currentColor;
}

:deep(.md-editor-preview .audio-toggle .audio-icon-pause) {
  display: none;
}

:deep(.md-editor-preview .audio-toggle.is-playing .audio-icon-play) {
  display: none;
}

:deep(.md-editor-preview .audio-toggle.is-playing .audio-icon-pause) {
  display: block;
}

:deep(.md-editor-preview .audio-progress) {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 0.6rem 0;
  cursor: pointer;
}

:deep(.md-editor-preview .audio-progress-track) {
  flex: 1;
  height: 4px;
  background: var(--bg-muted);
  border-radius: 2px;
  overflow: hidden;
}

:deep(.md-editor-preview .audio-progress-fill) {
  height: 100%;
  width: 0;
  background: var(--primary);
  border-radius: 2px;
}

:deep(.md-editor-preview .audio-time) {
  flex-shrink: 0;
  font-size: 0.75rem;
  color: var(--text-faint);
  white-space: nowrap;
}

:deep(.md-editor-preview .audio-volume) {
  position: relative;
  flex-shrink: 0;
}

:deep(.md-editor-preview .audio-volume-pop) {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  opacity: 0;
  visibility: hidden;
  padding: 0.5rem 0.6rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
  transition:
    opacity 0.15s ease,
    visibility 0.15s;
}

:deep(.md-editor-preview .audio-volume:hover .audio-volume-pop),
:deep(.md-editor-preview .audio-volume-pop:hover) {
  opacity: 1;
  visibility: visible;
}

:deep(.md-editor-preview .audio-volume-toggle) {
  flex-shrink: 0;
  width: 1.6rem;
  height: 1.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
}

:deep(.md-editor-preview .audio-volume-toggle svg) {
  width: 1.05rem;
  height: 1.05rem;
  fill: currentColor;
}

:deep(.md-editor-preview .audio-volume-toggle .audio-icon-vol-muted) {
  display: none;
}

:deep(.md-editor-preview .audio-volume-toggle.is-muted .audio-icon-vol) {
  display: none;
}

:deep(.md-editor-preview .audio-volume-toggle.is-muted .audio-icon-vol-muted) {
  display: block;
}

:deep(.md-editor-preview .audio-volume-bar) {
  position: relative;
  width: 4px;
  height: 4.5rem;
  background: var(--bg-muted);
  border-radius: 2px;
  overflow: hidden;
  cursor: pointer;
}

:deep(.md-editor-preview .audio-volume-fill) {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: var(--primary);
  border-radius: 2px;
}

:deep(.md-editor-preview .media-video-player) {
  width: 100%;
  aspect-ratio: 16 / 9;
  max-height: 70svh;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

:deep(.md-editor-preview .media-video-title) {
  display: block;
  margin-top: 0.4rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-strong);
  text-align: center;
}

:deep(.md-editor-preview .media-video-unsupported) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 0.5rem;
  padding: 0.6rem 0.9rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  background: var(--danger-soft);
  border: 1px solid var(--border);
  border-radius: 8px;
}

:deep(.md-editor-preview .media-card-download) {
  padding: 0.3rem 0.9rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--on-primary);
  background: var(--primary);
  border-radius: 6px;
  text-decoration: none;
}

.field > label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
}

.field input,
.field textarea,
.field select {
  padding: 0.6rem 0.75rem;
  font-family: inherit;
  font-size: 0.95rem;
  color: var(--text-strong);
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 8px;
  outline: none;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.field input:focus,
.field textarea:focus,
.field select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}

.cover-wrap {
  position: relative;
}

.cover-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  height: 14rem;
  padding: 1rem;
  border: 2px dashed var(--border-strong);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.6);
  color: var(--text-muted);
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.cover-dropzone:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.cover-dropzone span {
  font-size: 0.8rem;
  font-weight: 400;
  color: var(--text-faint);
}

.cover-dropzone input {
  display: none;
}

.cover-drag-mask {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  font-weight: 600;
  color: #fff;
  background: rgba(37, 99, 235, 0.65);
  border-radius: 12px;
  pointer-events: none;
}

.cover-preview-wrap {
  position: relative;
  height: 14rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-preview {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  background: rgba(15, 23, 42, 0.45);
  border-radius: 8px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.cover-preview-wrap:hover .cover-overlay {
  opacity: 1;
}

.cover-action {
  padding: 0.4rem 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.cover-action:hover {
  background: rgba(255, 255, 255, 0.32);
}

.cover-action input {
  display: none;
}

.media-refs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.75rem;
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
  border-radius: 8px;
}

.media-refs-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--primary-active);
}

.media-ref-tag {
  font-size: 0.8rem;
  color: var(--primary-active);
  background: var(--bg-card);
  border: 1px solid var(--primary-soft-border);
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
}

.hint {
  color: var(--text-faint);
  font-size: 0.85rem;
  margin: 0;
}

.error {
  color: var(--danger);
  font-size: 0.9rem;
  margin: 0;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1rem;
}

.btn-primary {
  padding: 0.6rem 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--on-primary);
  background: var(--primary);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    transform 0.15s ease;
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
  transform: translateY(-1px);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-ghost {
  padding: 0.6rem 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--primary);
  background: none;
  border: 1px solid var(--primary-soft-border);
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    transform 0.15s ease;
}

.btn-ghost:hover:not(:disabled) {
  background: var(--primary-soft);
  transform: translateY(-1px);
}

.btn-ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .editor {
    margin: 1rem 0.75rem 2rem;
    padding: 1.5rem 1.25rem;
  }
}
</style>
