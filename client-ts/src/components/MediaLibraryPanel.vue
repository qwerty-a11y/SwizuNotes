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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getArticleMedia } from '@/api/article'
import { deleteMedia, mediaUrl } from '@/api/media'
import MediaEditDialog from '@/components/MediaEditDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import { toast } from '@/utils/toast'
import type { MediaRef } from '@/types/article'
import type { MediaResponse } from '@/types/media'

const props = withDefaults(defineProps<{
  articleId: number
  refreshKey: number
  mediaRefs?: MediaRef[]
  coverId?: string
}>(), {
  mediaRefs: () => [],
  coverId: '',
})

const emit = defineEmits<{
  close: []
  /** 媒体数据变化：删除成功带被删 id（父组件据此清理引用）；编辑成功无 id（仅刷新） */
  changed: [deletedId: string | null]
  aliasSaved: [media: MediaResponse, oldAlias: string | null, newAlias: string]
}>()

const mediaList = ref<MediaResponse[]>([])
const loading = ref(false)
const deletingId = ref<string | null>(null)
const copiedId = ref<string | null>(null)
/** "已复制"提示复位定时器（卸载时清理） */
let copiedTimer: ReturnType<typeof setTimeout> | null = null
const editingMedia = ref<MediaResponse | null>(null)
const previewMedia = ref<MediaResponse | null>(null)

/** 资源引用名（alias）映射：media id → 正文引用别名 */
const aliasMap = computed(() => new Map(props.mediaRefs.map((r) => [r.id, r.alias])))

/** 除当前编辑媒体外的引用名（冲突校验用） */
const otherAliases = computed(() =>
  props.mediaRefs.filter((r) => r.id !== editingMedia.value?.id).map((r) => r.alias),
)

const TYPE_LABEL: Record<string, string> = {
  image: '图片',
  audio: '音频',
  video: '视频',
  file: '文件',
}

function parseName(media: MediaResponse): string {
  try {
    return JSON.parse(media.metadata).name || media.id.slice(0, 8)
  } catch {
    return media.id.slice(0, 8)
  }
}

function formatSize(size?: number | null): string {
  if (size == null) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function load(): Promise<void> {
  if (!props.articleId) return
  loading.value = true
  try {
    const all = (await getArticleMedia(props.articleId)).data
    // 文章标题图与音频封面图不展示：过滤掉封面用途的图片媒体
    const coverImgIds = new Set<string>()
    for (const m of all) {
      if (m.type !== 'audio') continue
      try {
        const meta = JSON.parse(m.metadata)
        if (meta.imageId) coverImgIds.add(meta.imageId)
      } catch {
        // 忽略解析失败
      }
    }
    mediaList.value = all.filter(
      (m) => !(m.type === 'image' && (m.id === props.coverId || coverImgIds.has(m.id))),
    )
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.refreshKey, load)

onBeforeUnmount(() => {
  if (copiedTimer) clearTimeout(copiedTimer)
})

/** 全屏遮罩预览：file 不支持预览，audio 为播放（autoplay），image/video 为预览 */
function openMedia(media: MediaResponse): void {
  if (media.type === 'file') return
  previewMedia.value = media
}

/** 复制 markdown 引用：图片带 ! 前缀（![名称](media://别名)），未引用媒体退回复制媒体 URL */
async function copyLink(media: MediaResponse): Promise<void> {
  const alias = aliasMap.value.get(media.id)
  const prefix = media.type === 'image' ? '!' : ''
  const text = alias ? `${prefix}[${parseName(media)}](media://${alias})` : mediaUrl(media.id)
  try {
    await navigator.clipboard.writeText(text)
    copiedId.value = media.id
    if (copiedTimer) clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => {
      if (copiedId.value === media.id) copiedId.value = null
    }, 1500)
  } catch {
    toast.error('复制失败，请手动复制')
  }
}

async function remove(media: MediaResponse): Promise<void> {
  const label = parseName(media)
  if (!window.confirm(`确定删除媒体「${label}」吗？删除后不可恢复。`)) return
  deletingId.value = media.id
  try {
    await deleteMedia(media.id)
    mediaList.value = mediaList.value.filter((m) => m.id !== media.id)
    emit('changed', media.id)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    deletingId.value = null
  }
}

function onUpdated(media: MediaResponse): void {
  const idx = mediaList.value.findIndex((m) => m.id === media.id)
  if (idx !== -1) mediaList.value[idx] = media
  editingMedia.value = null
  emit('changed', null)
}

function onAliasSaved(media: MediaResponse, oldAlias: string | null, newAlias: string): void {
  editingMedia.value = null
  emit('aliasSaved', media, oldAlias, newAlias)
}
</script>

<template>
  <Teleport to="#md-editor .md-editor-content">
    <aside class="media-library">
      <header class="library-header">
        <span class="library-title">媒体库</span>
        <button class="library-close" type="button" title="收起" @click="emit('close')">×</button>
      </header>

      <div class="library-body">
        <p v-if="loading" class="library-state">加载中…</p>
        <p v-else-if="!articleId" class="library-state">保存文章后即可查看媒体</p>
        <p v-else-if="!mediaList.length" class="library-state">暂无媒体，可通过工具栏上传</p>
        <ul v-else class="library-list">
          <li v-for="media in mediaList" :key="media.id" class="library-item">
            <span class="library-type" :class="`type-${media.type}`">{{ TYPE_LABEL[media.type] }}</span>
            <div class="library-item-body">
              <span class="library-name" :title="parseName(media)">{{ parseName(media) }}</span>
              <span class="library-size">引用：{{ aliasMap.get(media.id) || '未引用' }}</span>
              <span class="library-size" :title="media.id">{{ media.id.slice(0, 12) }}…</span>
            </div>
            <div class="library-actions">
              <button
                v-if="media.type !== 'file'"
                type="button"
                :title="media.type === 'audio' ? '播放' : '预览'"
                :aria-label="media.type === 'audio' ? '播放' : '预览'"
                @click="openMedia(media)"
              >
                <AppIcon v-if="media.type === 'audio'" name="play" />
                <AppIcon v-else name="eye" />
              </button>
              <button
                type="button"
                :title="copiedId === media.id ? '已复制' : '复制引用'"
                :aria-label="copiedId === media.id ? '已复制' : '复制引用'"
                :class="{ copied: copiedId === media.id }"
                @click="copyLink(media)"
              >
                <AppIcon name="link" />
              </button>
              <button type="button" title="编辑" aria-label="编辑" @click="editingMedia = media">
                <AppIcon name="write" />
              </button>
              <button
                type="button"
                class="danger"
                title="删除"
                aria-label="删除"
                :disabled="deletingId === media.id"
                @click="remove(media)"
              >
                <AppIcon name="trash" />
              </button>
            </div>
          </li>
        </ul>
      </div>

      <MediaEditDialog
        v-if="editingMedia"
        :media="editingMedia"
        :article-id="articleId"
        :alias="aliasMap.get(editingMedia.id) || ''"
        :existing-aliases="otherAliases"
        @close="editingMedia = null"
        @updated="onUpdated"
        @alias-saved="onAliasSaved"
      />
    </aside>
  </Teleport>

  <Teleport to="body">
    <Transition name="dialog" appear>
      <div v-if="previewMedia" class="preview-mask" @click.self="previewMedia = null">
        <div class="preview-dialog">
          <div class="preview-head">
            <span class="preview-title">{{ parseName(previewMedia) }}</span>
            <span class="preview-alias">引用：{{ aliasMap.get(previewMedia.id) || '未引用' }}</span>
            <button class="library-close" type="button" title="关闭" @click="previewMedia = null">×</button>
          </div>
          <div class="preview-content">
            <img v-if="previewMedia.type === 'image'" :src="mediaUrl(previewMedia.id)" alt="预览" />
            <video v-else-if="previewMedia.type === 'video'" :src="mediaUrl(previewMedia.id)" controls></video>
            <div v-else class="preview-audio">
              <audio :src="mediaUrl(previewMedia.id)" controls></audio>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.media-library {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  /* 需高于编辑器预览区 ArtPlayer UI（库内最高 z-index: 120），否则控制条浮在面板之上 */
  z-index: 200;
  width: 22rem;
  max-width: 80%;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-left: 1px solid var(--border);
  box-shadow: -8px 0 16px rgba(15, 23, 42, 0.12);
  animation: slide-in 0.25s ease;
}

@keyframes slide-in {
  from {
    transform: translateX(1.5rem);
    opacity: 0.4;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.library-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 0.9rem;
  border-bottom: 1px solid var(--border);
}

.library-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-strong);
}

.library-close {
  width: 1.6rem;
  height: 1.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  line-height: 1;
  color: var(--text-muted);
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.library-close:hover {
  background: var(--bg-muted);
}

.library-body {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.library-state {
  margin: 0;
  padding: 2rem 1rem;
  text-align: center;
  font-size: 0.85rem;
  color: var(--text-faint);
}

.library-state.error {
  color: var(--danger);
}

.library-list {
  list-style: none;
  margin: 0;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.library-item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  transition: border-color 0.15s ease;
}

.library-item:hover {
  border-color: var(--border-strong);
}

.library-type {
  flex-shrink: 0;
  font-size: 0.65rem;
  font-weight: 600;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
  color: var(--primary-active);
  background: var(--primary-soft);
}

.library-type.type-audio {
  color: #b45309;
  background: #fef3c7;
}

.library-type.type-video {
  color: #7c3aed;
  background: #ede9fe;
}

.library-type.type-file {
  color: #475569;
  background: #e2e8f0;
}

.library-item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.library-name {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.library-size {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.library-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.library-actions button {
  width: 1.6rem;
  height: 1.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  background: none;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.library-actions button:hover:not(:disabled) {
  color: var(--primary);
  background: var(--primary-soft);
}

.library-actions button svg {
  width: 1rem;
  height: 1rem;
  fill: currentColor;
}

.library-actions button.danger {
  color: var(--danger);
}

.library-actions button.danger:hover:not(:disabled) {
  color: var(--danger);
  background: var(--danger-soft);
}

.library-actions button.copied {
  color: #16a34a;
}

.library-actions button.copied:hover {
  color: #16a34a;
  background: var(--primary-soft);
}

.library-actions button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 全屏遮罩预览：z-index 需高于 md-editor 全屏根（库内写死 10000），否则全屏编辑器下遮罩被盖住 */
.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 10001;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-bg-strong);
  backdrop-filter: blur(6px);
}

.preview-dialog {
  width: min(56rem, calc(100vw - 2rem));
  max-height: calc(100vh - 4rem);
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-lift);
  overflow: hidden;
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.2s ease;
}

.dialog-enter-active .preview-dialog,
.dialog-leave-active .preview-dialog {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .preview-dialog,
.dialog-leave-to .preview-dialog {
  opacity: 0;
  transform: scale(0.95) translateY(12px);
}

.preview-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.9rem;
  border-bottom: 1px solid var(--border);
}

.preview-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-alias {
  flex-shrink: 0;
  font-size: 0.7rem;
  color: var(--text-faint);
}

.preview-head .library-close {
  margin-left: auto;
}

.preview-content {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  overflow-y: auto;
  background: var(--bg-muted);
}

.preview-content img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 8px;
}

.preview-content video {
  max-width: 100%;
  max-height: 100%;
  border-radius: 8px;
}

.preview-audio {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.preview-audio audio {
  width: 100%;
  max-width: 40rem;
}
</style>
