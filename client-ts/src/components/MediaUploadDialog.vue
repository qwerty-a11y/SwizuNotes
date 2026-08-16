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
import { ref, watch } from 'vue'
import { parseBlob } from 'music-metadata'
import { uploadMedia } from '@/api/media'
import { MEDIA_ALIAS_PATTERN, uniqueAlias } from '@/utils/articleContent'
import MediaCoverPicker from '@/components/MediaCoverPicker.vue'
import type { MediaCategory, MediaResponse } from '@/types/media'

const props = withDefaults(defineProps<{
  type: MediaCategory
  articleId: number
  file?: File | null
  existingAliases?: string[]
}>(), {
  file: null,
  existingAliases: () => [],
})

const emit = defineEmits<{
  close: []
  uploaded: [media: MediaResponse, alias: string]
}>()

const file = ref<File | null>(null)
const name = ref('')
const alias = ref('')
const aliasEdited = ref(false)

const ALIAS_PREFIX: Record<MediaCategory, string> = {
  image: 'img',
  audio: 'audio',
  video: 'video',
  file: 'file',
}

function generateAlias(): string {
  return uniqueAlias(props.existingAliases, ALIAS_PREFIX[props.type])
}

function onAliasInput(): void {
  aliasEdited.value = true
}
const duration = ref<number | null>(null)
const coverBlob = ref<Blob | null>(null)
const analyzing = ref(false)
const uploading = ref(false)
const error = ref('')
const visible = ref(true)

const CLOSE_ANIM_MS = 200

/** 播放关闭动画后执行回调 */
function closeWithCallback(cb: () => void): void {
  visible.value = false
  setTimeout(cb, CLOSE_ANIM_MS)
}

const typeLabel = {
  image: '图片',
  audio: '音频',
  video: '视频',
  file: '文件',
}[props.type]

const accept = {
  image: 'image/*',
  audio: 'audio/*',
  video: 'video/*',
  file: '',
}[props.type]

watch(
  () => props.file,
  (f) => {
    if (f) void analyze(f)
  },
  { immediate: true },
)

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (f) void analyze(f)
}

function onDrop(event: DragEvent): void {
  event.preventDefault()
  const f = event.dataTransfer?.files?.[0]
  if (f) void analyze(f)
}

async function analyze(f: File): Promise<void> {
  file.value = f
  name.value = f.name.replace(/\.[^.]+$/, '')
  // 用户改过别名：保留输入；但换文件后若与已有引用名冲突（或为空）则回退自动生成
  if (aliasEdited.value) {
    const cur = alias.value.trim()
    if (!cur || props.existingAliases.includes(cur)) alias.value = generateAlias()
  } else {
    alias.value = generateAlias()
  }
  duration.value = null
  coverBlob.value = null
  error.value = ''
  analyzing.value = true
  try {
    if (props.type === 'audio') {
      const meta = await parseBlob(f)
      if (meta.common.title) name.value = meta.common.title
      if (meta.format.duration) duration.value = Math.round(meta.format.duration)
      const picture = meta.common.picture?.[0]
      if (picture) {
        coverBlob.value = new Blob([picture.data], { type: picture.format })
      }
    } else if (props.type === 'video') {
      duration.value = await probeVideoDuration(f)
    }
  } catch {
    // 解析失败时使用文件名，音频/视频时长留空
  } finally {
    analyzing.value = false
  }
}

function probeVideoDuration(f: File): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(f)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      const d = Number.isFinite(video.duration) ? Math.round(video.duration) : null
      URL.revokeObjectURL(url)
      resolve(d)
    }
    video.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    video.src = url
  })
}

async function confirm(): Promise<void> {
  if (!file.value || analyzing.value) return
  if (!props.articleId) {
    error.value = '请先保存文章，再上传文件'
    return
  }
  const finalAlias = alias.value.trim() || generateAlias()
  if (!MEDIA_ALIAS_PATTERN.test(finalAlias)) {
    error.value = '别名仅限小写字母/数字/_/-，以字母开头，1-32 位'
    return
  }
  if (props.existingAliases.includes(finalAlias)) {
    error.value = `别名 ${finalAlias} 已被使用，请更换`
    return
  }
  error.value = ''
  uploading.value = true
  try {
    const finalName = name.value.trim() || file.value.name
    if (props.type === 'audio') {
      let imageId: string | null = null
      if (coverBlob.value) {
        const coverFile = new File([coverBlob.value], 'cover.jpg', { type: coverBlob.value.type })
        const coverResult = (await uploadMedia(coverFile, props.articleId, 'image', JSON.stringify({ name: '封面' }))).data
        imageId = coverResult.id
      }
      const result = await uploadMedia(file.value, props.articleId, 'audio', JSON.stringify({
        name: finalName,
        imageId,
        duration: duration.value,
      }))
      closeWithCallback(() => emit('uploaded', result.data, finalAlias))
    } else if (props.type === 'video') {
      const result = await uploadMedia(file.value, props.articleId, 'video', JSON.stringify({
        name: finalName,
        duration: duration.value,
      }))
      closeWithCallback(() => emit('uploaded', result.data, finalAlias))
    } else {
      const result = await uploadMedia(file.value, props.articleId, props.type, JSON.stringify({ name: finalName }))
      closeWithCallback(() => emit('uploaded', result.data, finalAlias))
    }
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog" appear>
      <div v-if="visible" class="upload-dialog-mask" @click.self="closeWithCallback(() => emit('close'))">
        <div class="upload-dialog">
          <h3 class="dialog-title">上传{{ typeLabel }}</h3>

        <label class="dialog-dropzone" @dragover.prevent @drop="onDrop">
          <input type="file" :accept="accept" @change="onFileChange" />
          <template v-if="!file">点击选择或拖拽{{ typeLabel }}文件到此处</template>
          <template v-else>{{ file.name }}</template>
        </label>
        <p v-if="analyzing" class="dialog-hint">正在解析文件…</p>

        <template v-if="file && !analyzing">
          <div class="dialog-field">
            <label>名称</label>
            <input v-model="name" maxlength="100" placeholder="文件名" />
          </div>
          <div class="dialog-field">
            <label>别名（引用名称）</label>
            <input v-model="alias" maxlength="32" placeholder="如 img1" @input="onAliasInput" />
            <p class="dialog-hint">正文中通过 media://别名 引用，仅限小写字母/数字/_/-，以字母开头，1-32 位</p>
          </div>
          <div v-if="type === 'audio'" class="dialog-field">
            <label>封面</label>
            <MediaCoverPicker v-model="coverBlob" />
          </div>
          <div v-if="type === 'audio' || type === 'video'" class="dialog-field">
            <label>时长（秒）</label>
            <input v-model.number="duration" type="number" min="0" placeholder="自动检测，可修改" />
          </div>
        </template>

        <p v-if="error" class="dialog-error">{{ error }}</p>

        <div class="dialog-actions">
          <button class="dialog-btn" type="button" @click="closeWithCallback(() => emit('close'))">取消</button>
          <button class="dialog-btn primary" type="button" :disabled="!file || analyzing || uploading" @click="confirm">
            {{ uploading ? '上传中…' : '上传' }}
          </button>
        </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.upload-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-bg);
  backdrop-filter: blur(6px);
}

.upload-dialog {
  width: 26rem;
  max-width: calc(100vw - 2rem);
  max-height: calc(100vh - 4rem);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1.5rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-lift);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.2s ease;
}

.dialog-enter-active .upload-dialog,
.dialog-leave-active .upload-dialog {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .upload-dialog,
.dialog-leave-to .upload-dialog {
  opacity: 0;
  transform: scale(0.95) translateY(12px);
}

.dialog-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.dialog-dropzone {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 5rem;
  padding: 0.75rem;
  border: 2px dashed var(--border-strong);
  border-radius: 10px;
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.dialog-dropzone:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.dialog-dropzone input {
  display: none;
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.dialog-field > label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
}

.dialog-field input {
  padding: 0.5rem 0.7rem;
  font-family: inherit;
  font-size: 0.9rem;
  color: var(--text-strong);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  outline: none;
}

.dialog-field input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}

.dialog-hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-faint);
}

.dialog-error {
  margin: 0;
  font-size: 0.85rem;
  color: var(--danger);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.dialog-btn {
  padding: 0.45rem 1.2rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-muted);
  background: none;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.dialog-btn:hover {
  background: var(--bg-muted);
}

.dialog-btn.primary {
  color: var(--on-primary);
  background: var(--primary);
  border-color: var(--primary);
}

.dialog-btn.primary:hover {
  background: var(--primary-hover);
}

.dialog-btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
