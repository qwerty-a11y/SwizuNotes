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
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { DropdownToolbar, MdEditor, NormalToolbar } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import DOMPurify from 'dompurify'
import { createArticle, getArticle, updateArticle } from '@/api/article'
import { API_BASE, MEDIA_TOKEN_KEY } from '@/api/http'
import { mediaUrl, uploadMedia } from '@/api/media'
import { mediaCardHtml, MEDIA_ALIAS_PATTERN, normalizeContent, uniqueAlias } from '@/utils/articleContent'
import { toast } from '@/utils/toast'
import { useMediaCards } from '@/composables/useMediaCards'
import MediaUploadDialog from '@/components/MediaUploadDialog.vue'
import MediaLibraryPanel from '@/components/MediaLibraryPanel.vue'
import AppIcon from '@/components/AppIcon.vue'
import { THEME_CHANGE_EVENT, getStoredTheme, isDark } from '@/theme'
import type { ArticleContent, ArticleStatus, MediaRef } from '@/types/article'
import type { MediaCategory, MediaResponse } from '@/types/media'

const route = useRoute()
const router = useRouter()

/** 编辑器语法高亮主题：跟随网站主题（深色用 md-editor-v3 内置 dark + 深色代码主题） */
const editorTheme = ref<'light' | 'dark'>(isDark(getStoredTheme()) ? 'dark' : 'light')

/** 主题切换时同步编辑器配色（具名函数，供 onMounted 注册 / onBeforeUnmount 移除，避免监听泄漏） */
function onThemeChange(): void {
  editorTheme.value = isDark(getStoredTheme()) ? 'dark' : 'light'
}

const routeArticleId = Number(route.params.articleId) || 0
/** 新文章进入编辑页时自动创建草稿，保证随时可上传文件（后端复用已预留的空白草稿 id） */
const articleId = ref(routeArticleId)
const title = ref('')
const cover = ref('')
const summary = ref('')
const content = ref<ArticleContent>({ body: '', mediaRefs: [] })
const status = ref<ArticleStatus>('draft')
const originalStatus = ref<ArticleStatus>('draft')
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
  window.addEventListener(THEME_CHANGE_EVENT, onThemeChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.removeEventListener(THEME_CHANGE_EVENT, onThemeChange)
  mq.removeEventListener('change', onMqChange)
})

onBeforeRouteLeave(() => {
  if (hasUnsavedChanges()) {
    if (!window.confirm('有未保存的更改，确定离开吗？')) {
      return false
    }
  }
})

/**
 * 移动端（≤900px）：默认仅编辑（preview=false 隐藏预览），
 * 工具栏预览按钮为 previewOnly（仅预览占满编辑区，再点返回编辑）。
 * 注意：md-editor-v3 的 preview prop 是 boolean（true=分栏显示预览），
 * 传字符串会被 Vue 转成 true 导致分栏，必须用布尔值。
 */
const isMobile = ref(false)
const showPreview = ref(true)

const mq = window.matchMedia('(max-width: 900px)')
isMobile.value = mq.matches
showPreview.value = !mq.matches
function onMqChange(e: MediaQueryListEvent): void {
  isMobile.value = e.matches
  showPreview.value = !e.matches
  // 跨断点：显式重置编辑器内部预览状态（rerender 只重绘不重置 setting；
  // preview prop 仅在初始化时生效，后续变化不驱动内部状态）
  void nextTick(() => {
    editorRef.value?.togglePreviewOnly(false)
    editorRef.value?.togglePreview(!e.matches)
  })
}
mq.addEventListener('change', onMqChange)

/** 工具栏：移动端预览按钮用 previewOnly（仅预览占满编辑区），桌面端保持 live 分栏切换 */
const toolbarList = computed(() => [
  'bold',
  'italic',
  'strikeThrough',
  'sub',
  'sup',
  'quote',
  'orderedList',
  'unorderedList',
  'task',
  'link',
  'code',
  'codeRow',
  'table',
  0,
  'divider',
  isMobile.value ? 'previewOnly' : 'preview',
  'fullscreen',
  'catalog',
  1,
])

const editorRef = ref<{
  insert: (generate: (selectedText: string) => { targetValue: string; select?: boolean; deviationStart?: number; deviationEnd?: number }) => void
  focus: (options: 'end') => void
  toggleCatalog: (status?: boolean) => void
  rerender: () => void
  togglePreview: (status?: boolean) => void
  togglePreviewOnly: (status?: boolean) => void
} | null>(null)
const editorWrap = ref<HTMLElement | null>(null)
const mediaCards = useMediaCards()
let fillTimer: ReturnType<typeof setTimeout> | null = null
let previewObserver: MutationObserver | null = null

const dialogType = ref<MediaCategory | null>(null)
const dialogFile = ref<File | null>(null)
const dropdownVisible = ref(false)

const libraryOpen = ref(false)
/** 上传/删除媒体后 +1，通知媒体库面板刷新 */
const mediaRefreshKey = ref(0)
const libraryTrigger = h(AppIcon, { name: 'library', size: '1.1rem' })

const uploadTrigger = h(AppIcon, { name: 'upload', size: '1.1rem' })

/** 自定义工具栏：上传媒体文件悬停菜单（toolbars 中以数字 0 引用，visible 受控 + computed 响应式重建）；媒体库按钮（数字 1） */
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
  h(
    NormalToolbar,
    {
      title: '媒体库',
      onClick: () => {
        libraryOpen.value = !libraryOpen.value
        // 与内置目录互斥：打开媒体库时收起目录
        editorRef.value?.toggleCatalog(false)
      },
    },
    {
      default: () => h('span', { class: 'library-trigger' }, [libraryTrigger]),
    },
  ),
])

const mediaRefMap = computed(() => new Map(content.value.mediaRefs.map((ref) => [ref.alias, ref])))
const mediaAliases = computed(() => content.value.mediaRefs.map((ref) => ref.alias))

function openUploadDialog(type: MediaCategory, file?: File | null): void {
  dropdownVisible.value = false
  dialogType.value = type
  dialogFile.value = file ?? null
}

function onMediaUploaded(media: MediaResponse, alias: string): void {
  const finalAlias =
    MEDIA_ALIAS_PATTERN.test(alias) && !mediaAliases.value.includes(alias)
      ? alias
      : uniqueAlias(content.value.mediaRefs)
  content.value.mediaRefs.push({ id: media.id, type: media.type, alias: finalAlias } satisfies MediaRef)
  let label = ''
  try {
    label = JSON.parse(media.metadata).name || ''
  } catch {
    // 名称解析失败则用 alias
  }
  const md =
    media.type === 'image' ? `![${label}](media://${finalAlias})` : `[${label}](media://${finalAlias})`
  // 追加到正文末尾：先把光标移到文末再插入（insert 需传生成函数，走编辑器内部命令，可撤销）
  editorRef.value?.focus('end')
  editorRef.value?.insert(() => ({ targetValue: md, select: false }))
  dialogType.value = null
  mediaRefreshKey.value += 1
  // 上传即保存，避免刷新后引用表丢失
  void silentSave()
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

/** 预览中媒体 URL（草稿媒体附加媒体专用令牌） */
function toMediaUrl(id: string): string {
  const token = localStorage.getItem(MEDIA_TOKEN_KEY)
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
      toast.error((e as Error).message)
    }
  } else {
    await ensureDraft()
  }
})

/** 新文章：立即创建空草稿，解锁媒体上传；后端会复用已预留的空白草稿 id，拿到 id 后写入 URL */
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
    router.replace(`/editor/${result.id}`)
    snapshotForm()
  } catch (e) {
    toast.error(`自动创建草稿失败：${(e as Error).message}`)
  }
}

function onCoverUploaded(media: MediaResponse): void {
  cover.value = media.id
  mediaRefreshKey.value += 1
}

function uploadCoverFile(file: File): void {
  if (!articleId.value) {
    toast.error('请先保存文章，再上传封面图')
    return
  }
  if (!file.type.startsWith('image/')) {
    toast.error('封面图必须是图片文件')
    return
  }
  uploadMedia(file, articleId.value, 'image')
    .then((result) => onCoverUploaded(result.data))
    .catch((e) => {
      toast.error((e as Error).message)
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
    toast.error('请先保存文章，再上传图片')
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
      mediaRefreshKey.value += 1
    } catch (e) {
      toast.error((e as Error).message)
    }
  }
  callback([], texts)
  // 上传即保存引用表
  void silentSave()
}

/** 静默保存（上传后/定时）：保持原发布状态，成功后更新"未保存"基准 */
let savingSilent = false
let autosaveTimer: ReturnType<typeof setTimeout> | null = null
async function silentSave(): Promise<void> {
  if (!articleId.value || savingSilent) return
  // 全空不自动保存：避免无意义的空内容保存请求
  if (
    title.value === '' &&
    summary.value === '' &&
    !cover.value &&
    content.value.body === '' &&
    content.value.mediaRefs.length === 0
  ) {
    return
  }
  savingSilent = true
  try {
    await updateArticle(articleId.value, {
      title: title.value,
      cover: cover.value,
      content: { body: content.value.body, mediaRefs: content.value.mediaRefs },
      summary: summary.value,
      status: originalStatus.value,
    })
    snapshotForm()
  } catch (e) {
    toast.error(`自动保存失败：${(e as Error).message}`)
  } finally {
    savingSilent = false
  }
}

/** 正文编辑停止 3 秒后自动保存（防抖） */
function scheduleAutosave(): void {
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => {
    void silentSave()
  }, 3000)
}

watch(content.body, scheduleAutosave)

/** 引用名重命名：同步正文所有 media://旧名 引用与 mediaRefs，并自动保存 */
function onAliasSaved(media: MediaResponse, oldAlias: string | null, newAlias: string): void {
  if (!newAlias || newAlias === oldAlias) return
  if (oldAlias) {
    // 负向前瞻防止 media://img1 误伤 media://img11
    content.value.body = content.value.body.replace(
      new RegExp(`media://${oldAlias}(?![a-z0-9_-])`, 'g'),
      `media://${newAlias}`,
    )
  }
  const ref = content.value.mediaRefs.find((r) => r.alias === oldAlias)
  if (ref) {
    ref.alias = newAlias
  } else {
    content.value.mediaRefs.push({ id: media.id, type: media.type, alias: newAlias } satisfies MediaRef)
  }
  void silentSave()
}

/**
 * 媒体库删除媒体（@changed 带被删 id）：同步清理 content.mediaRefs 与正文 media:// 引用
 * （避免悬空引用/预览破图/保存脏数据），封面引用一并清除，随后自动保存。
 * 编辑元数据成功（changed 无 id）只触发刷新，不做清理。
 */
function onLibraryChanged(deletedId: string | null): void {
  if (!deletedId) return
  const removed = content.value.mediaRefs.filter((r) => r.id === deletedId)
  if (!removed.length) return
  content.value.mediaRefs = content.value.mediaRefs.filter((r) => r.id !== deletedId)
  for (const ref of removed) {
    // 移除正文中的 media://引用：图片引用保留 alt 文字，链接引用保留标签文字
    const esc = ref.alias.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    content.value.body = content.value.body
      .replace(new RegExp(`!\\[[^\\]]*\\]\\(media://${esc}\\)`, 'g'), (m) => m.match(/^!\[([^\]]*)\]/)?.[1] ?? '')
      .replace(new RegExp(`\\[[^\\]]*\\]\\(media://${esc}\\)`, 'g'), (m) => m.match(/^\[([^\]]*)\]/)?.[1] ?? '')
  }
  // 封面引用同步清理
  if (cover.value === deletedId) cover.value = ''
  void silentSave()
}

/** 预览渲染：media:// 图片替换为可显示 URL，非图片链接渲染为与文章页一致的多媒体卡片；结果消毒（禁 script/事件属性） */
function previewSanitize(html: string): string {
  return DOMPurify.sanitize(
    html
      .replace(/<img src="media:\/\/([a-z0-9_-]+)"([^>]*)>/g, (match, alias: string) => {
        const ref = mediaRefMap.value.get(alias)
        return ref ? `<img src="${toMediaUrl(ref.id)}" alt="">` : match
      })
      .replace(/<a href="media:\/\/([a-z0-9_-]+)"[^>]*>([\s\S]*?)<\/a>/g, (match, alias: string) => {
        const ref = mediaRefMap.value.get(alias)
        if (!ref) return match
        return mediaCardHtml(ref, toMediaUrl(ref.id))
      }),
  )
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

/** 目录与媒体库互斥：目录展开时关闭媒体库 */
function syncCatalogExclusive(): void {
  if (!libraryOpen.value) return
  const catalogEl = editorWrap.value?.querySelector('.md-editor-catalog-editor')
  if (catalogEl) libraryOpen.value = false
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
      if (target.closest?.('.media-card, .media-video, .media-library')) continue
      needsFill = true
    }
    if (needsFill) scheduleFillPreview()
    syncCatalogExclusive()
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
  if (autosaveTimer) clearTimeout(autosaveTimer)
  mediaCards.dispose()
})

async function saveWithStatus(saveStatus: ArticleStatus): Promise<void> {
  loading.value = true
  try {
    if (!articleId.value) {
      // 新文章：等待自动草稿创建完成（避免与 onMounted 的 ensureDraft 并发创建重复文章）
      await ensureDraft()
    }
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
    toast.error((e as Error).message)
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
          id="md-editor"
          v-model="content.body"
          :on-upload-img="onUploadImg"
          :sanitize="previewSanitize"
          :def-toolbars="defToolbars"
          :preview="showPreview"
          :toolbars="toolbarList"
          :theme="editorTheme"
          :code-theme="editorTheme === 'dark' ? 'atom' : 'github'"
          style="height: 520px"
          language="zh-CN"
        />
        <MediaLibraryPanel
          v-if="libraryOpen"
          :article-id="articleId"
          :refresh-key="mediaRefreshKey"
          :media-refs="content.mediaRefs"
          :cover-id="cover"
          @close="libraryOpen = false"
          @alias-saved="onAliasSaved"
          @changed="onLibraryChanged"
        />
      </div>
      <p class="hint">支持图片/音频/视频/文件：工具栏悬停上传或直接拖拽文件到编辑器，按扩展名自动归类；音频自动提取 ID3 封面、标题与时长</p>
    </div>

    <MediaUploadDialog
      v-if="dialogType"
      :type="dialogType"
      :article-id="articleId"
      :file="dialogFile"
      :existing-aliases="mediaAliases"
      @close="dialogType = null"
      @uploaded="onMediaUploaded"
    />

    <div class="footer">
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
  background: color-mix(in srgb, var(--bg-card) 75%, transparent);
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
  /* 滚动条 track/thumb 用主题文本色派生，避免库默认 #e2e2e2 在浅色下过白、深色下突兀 */
  --md-scrollbar-bg-color: color-mix(in srgb, var(--text-faint) 18%, transparent);
  --md-scrollbar-thumb-color: color-mix(in srgb, var(--text-faint) 55%, transparent);
  --md-scrollbar-thumb-hover-color: color-mix(in srgb, var(--text-faint) 75%, transparent);
  --md-scrollbar-thumb-active-color: color-mix(in srgb, var(--text-faint) 75%, transparent);
}

:deep(.md-editor.md-editor-fullscreen) {
  --md-bk-color: var(--bg-card);
  --md-bk-color-outstand: var(--bg-muted);
  --md-bk-hover-color: var(--bg-muted);
  background-color: var(--bg-card);
}

.editor-fullscreen-active {
  z-index: 200;
}

:deep(.upload-trigger),
:deep(.library-trigger) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

/* 上传/表格等展开菜单：库样式背景用 --md-bk-color（被设为 transparent），需显式底色 */
:deep(.md-editor-dropdown) {
  background-color: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: var(--shadow-card);
}

/* 内置目录面板：同理（background-color: var(--md-bk-color)） */
:deep(.md-editor-catalog-editor) {
  background-color: var(--bg-card);
}

:deep(.md-editor-menu) {
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.editor-wrap {
  position: relative;
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

/* 名称行（名称 + 下载按钮），播放器控件在下一行 */
:deep(.md-editor-preview .media-card-head) {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

:deep(.md-editor-preview .media-card-head .media-card-name) {
  flex: 1;
  min-width: 0;
}

/* 窄卡片（useMediaCards 按卡片自身宽度 ≤ 480px 加 is-narrow）：压缩封面/控件尺寸，隐藏时间文本 */
:deep(.md-editor-preview .media-card.is-narrow) {
  gap: 0.6rem;
  padding: 0.6rem 0.75rem;
}

:deep(.md-editor-preview .media-card.is-narrow .media-card-cover) {
  width: 3.25rem;
  height: 3.25rem;
}

:deep(.md-editor-preview .media-card.is-narrow .audio-player) {
  gap: 0.4rem;
}

:deep(.md-editor-preview .media-card.is-narrow .audio-toggle) {
  width: 2rem;
  height: 2rem;
}

:deep(.md-editor-preview .media-card.is-narrow .audio-toggle svg) {
  width: 1rem;
  height: 1rem;
}

:deep(.md-editor-preview .media-card.is-narrow .audio-time) {
  display: none;
}

:deep(.md-editor-preview .media-card.is-narrow .audio-volume-toggle) {
  width: 1.4rem;
  height: 1.4rem;
}

:deep(.md-editor-preview .media-card.is-narrow .audio-volume-toggle svg) {
  width: 0.95rem;
  height: 0.95rem;
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

/* 文件卡片格式图标：颜色跟随主题色，窄卡片时略缩小 */
:deep(.md-editor-preview .media-card-file-icon) {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
}

:deep(.md-editor-preview .media-card-file-icon svg) {
  width: 2.4rem;
  height: 2.4rem;
  fill: currentColor;
}

:deep(.md-editor-preview .media-card.is-narrow .media-card-file-icon svg) {
  width: 2rem;
  height: 2rem;
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
  /* 触屏滑动调节时禁止页面滚动 */
  touch-action: none;
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

@media (hover: hover) {
  :deep(.md-editor-preview .audio-volume:hover .audio-volume-pop),
  :deep(.md-editor-preview .audio-volume-pop:hover) {
    opacity: 1;
    visibility: visible;
  }
}

/* 触屏设备：点击音量按钮加 open class 展开（无 hover） */
:deep(.md-editor-preview .audio-volume-pop.open) {
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
  /* 触屏滑动调节时禁止页面滚动 */
  touch-action: none;
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

/* 窄容器（is-narrow ≤480px）：控制条控件溢出被裁剪，
   隐藏非核心控件（倍速/画中画/网页全屏）并缩小控制条尺寸，保证完整展示 */
:deep(.md-editor-preview .media-video.is-narrow .art-video-player) {
  --art-control-height: 34px;
  --art-control-icon-size: 26px;
  --art-control-icon-scale: 1;
}

:deep(.md-editor-preview .media-video.is-narrow .art-control-playbackRate),
:deep(.md-editor-preview .media-video.is-narrow .art-control-pip),
:deep(.md-editor-preview .media-video.is-narrow .art-control-fullscreenWeb) {
  display: none;
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
  color: var(--on-primary);
  background: var(--drag-mask-bg);
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
  background: var(--overlay-bg);
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
  color: var(--overlay-btn-text);
  background: var(--overlay-btn-bg);
  border: 1px solid var(--overlay-btn-border);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.cover-action:hover {
  background: var(--overlay-btn-bg-hover);
}

.cover-action input {
  display: none;
}

.hint {
  color: var(--text-faint);
  font-size: 0.85rem;
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
