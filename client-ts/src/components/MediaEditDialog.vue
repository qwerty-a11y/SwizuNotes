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
import { mediaUrl, uploadMedia, updateMediaMetadata } from '@/api/media'
import { MEDIA_ALIAS_PATTERN } from '@/utils/articleContent'
import MediaCoverPicker from '@/components/MediaCoverPicker.vue'
import type { MediaResponse } from '@/types/media'

const props = withDefaults(defineProps<{
  media: MediaResponse
  articleId: number
  alias?: string
  existingAliases?: string[]
}>(), {
  alias: '',
  existingAliases: () => [],
})

const emit = defineEmits<{
  close: []
  updated: [media: MediaResponse]
  aliasSaved: [media: MediaResponse, oldAlias: string | null, newAlias: string]
}>()

const typeLabel = {
  image: '图片',
  audio: '音频',
  video: '视频',
  file: '文件',
}[props.media.type]

const name = ref('')
const alias = ref(props.alias)
const duration = ref<number | null>(null)
const coverBlob = ref<Blob | null>(null)
/** 当前封面预览地址：新选择为 blob URL，未动为原封面 URL，移除为 '' */
const coverSrc = ref('')
/** 原封面媒体的权威 imageId（直取 metadata，不做 URL 正则反解） */
const initialImageId = ref<string | null>(null)
const initialCoverUrl = ref('')
const saving = ref(false)
const error = ref('')
const visible = ref(true)

const CLOSE_ANIM_MS = 200

function closeWithCallback(cb: () => void): void {
  visible.value = false
  setTimeout(cb, CLOSE_ANIM_MS)
}

try {
  const meta = JSON.parse(props.media.metadata)
  name.value = meta.name || ''
  duration.value = meta.duration ?? null
  if (props.media.type === 'audio' && meta.imageId) {
    initialImageId.value = meta.imageId
    initialCoverUrl.value = mediaUrl(meta.imageId)
    coverSrc.value = initialCoverUrl.value
  }
} catch {
  // metadata 解析失败按空处理
}

function onCoverChange(src: string): void {
  coverSrc.value = src
}

async function confirm(): Promise<void> {
  if (saving.value) return
  error.value = ''
  // 引用名校验：已引用媒体不可清空；格式 + 唯一性冲突检查
  const newAlias = alias.value.trim()
  if (props.alias && !newAlias) {
    error.value = '引用名不能为空'
    return
  }
  if (newAlias && !MEDIA_ALIAS_PATTERN.test(newAlias)) {
    error.value = '引用名仅限小写字母/数字/_/-，以字母开头，1-32 位'
    return
  }
  if (newAlias && props.existingAliases.includes(newAlias)) {
    error.value = `引用名 ${newAlias} 已被使用，请更换`
    return
  }
  saving.value = true
  try {
    const finalName = name.value.trim()
    if (props.media.type === 'audio') {
      let imageId: string | null = null
      if (coverBlob.value) {
        // 新选了封面：先上传为图片媒体
        const coverFile = new File([coverBlob.value], 'cover.jpg', { type: coverBlob.value.type })
        const coverResult = (await uploadMedia(coverFile, props.articleId, 'image', JSON.stringify({ name: '封面' }))).data
        imageId = coverResult.id
      } else if (coverSrc.value) {
        // 未更换封面：保留原封面 imageId（直取 metadata，不反解 URL）
        imageId = initialImageId.value
      }
      const result = await updateMediaMetadata(props.media.id, JSON.stringify({
        name: finalName,
        imageId,
        duration: duration.value,
      }))
      closeWithCallback(() => emit('updated', result.data))
    } else if (props.media.type === 'video') {
      const result = await updateMediaMetadata(props.media.id, JSON.stringify({
        name: finalName,
        duration: duration.value,
      }))
      closeWithCallback(() => emit('updated', result.data))
    } else {
      const result = await updateMediaMetadata(props.media.id, JSON.stringify({ name: finalName }))
      closeWithCallback(() => emit('updated', result.data))
    }
    // 仅当元数据保存成功后才下发引用名变更（保存失败时父组件不应同步正文引用，避免状态不一致）
    if (newAlias !== props.alias) {
      emit('aliasSaved', props.media, props.alias || null, newAlias)
    }
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog" appear>
      <div v-if="visible" class="edit-dialog-mask" @click.self="closeWithCallback(() => emit('close'))">
        <div class="edit-dialog">
          <h3 class="dialog-title">编辑{{ typeLabel }}信息</h3>

          <div class="dialog-field">
            <label>名称</label>
            <input v-model="name" maxlength="100" placeholder="文件名" />
          </div>
          <div class="dialog-field">
            <label>引用名</label>
            <input v-model="alias" maxlength="32" placeholder="如 img1，正文中通过 media://引用名 引用" />
            <p class="dialog-hint">修改后正文中所有 media://{{ props.alias }} 引用将自动同步为新名称</p>
          </div>
          <div v-if="media.type === 'audio'" class="dialog-field">
            <label>封面</label>
            <MediaCoverPicker :initial-src="initialCoverUrl" v-model="coverBlob" @cover="onCoverChange" />
          </div>
          <div v-if="media.type === 'audio' || media.type === 'video'" class="dialog-field">
            <label>时长（秒）</label>
            <input v-model.number="duration" type="number" min="0" placeholder="可留空" />
          </div>

          <p v-if="error" class="dialog-error">{{ error }}</p>

          <div class="dialog-actions">
            <button class="dialog-btn" type="button" @click="closeWithCallback(() => emit('close'))">取消</button>
            <button class="dialog-btn primary" type="button" :disabled="saving" @click="confirm">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.edit-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-bg);
  backdrop-filter: blur(6px);
}

.edit-dialog {
  width: 26rem;
  max-width: calc(100vw - 2rem);
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

.dialog-enter-active .edit-dialog,
.dialog-leave-active .edit-dialog {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .edit-dialog,
.dialog-leave-to .edit-dialog {
  opacity: 0;
  transform: scale(0.95) translateY(12px);
}

.dialog-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
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

.dialog-error {
  margin: 0;
  font-size: 0.85rem;
  color: var(--danger);
}

.dialog-hint {
  margin: 0;
  font-size: 0.78rem;
  color: var(--text-faint);
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
