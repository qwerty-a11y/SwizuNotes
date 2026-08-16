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
import { onBeforeUnmount, ref } from 'vue'

const props = withDefaults(defineProps<{
  /** 已有封面预览地址（如媒体 imageId 的远程 URL），未更换时保留 */
  initialSrc?: string
  /** 是否允许移除封面（默认允许；主题 banner 等"必须有图"的资源传 false，仅可更换不可移除） */
  removable?: boolean
}>(), {
  initialSrc: '',
  removable: true,
})

const emit = defineEmits<{
  /** 封面变化（新选择/移除），参数为当前预览 src（'' 表示无封面） */
  cover: [src: string]
}>()

/** 当前选中的封面文件（v-model），null = 未新选或已移除 */
const model = defineModel<Blob | null>({ default: null })

const src = ref(props.initialSrc)
const dragOver = ref(false)
const wrapEl = ref<HTMLElement | null>(null)

function pick(file: File): void {
  if (model.value) URL.revokeObjectURL(src.value)
  model.value = file
  src.value = URL.createObjectURL(file)
  emit('cover', src.value)
}

function clear(): void {
  if (!props.removable) return
  if (model.value) URL.revokeObjectURL(src.value)
  model.value = null
  src.value = ''
  emit('cover', src.value)
}

function onPick(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file && file.type.startsWith('image/')) pick(file)
}

function onDragEnter(): void {
  dragOver.value = true
}

function onDragLeave(event: DragEvent): void {
  if (!wrapEl.value?.contains(event.relatedTarget as Node)) {
    dragOver.value = false
  }
}

function onDrop(event: DragEvent): void {
  event.preventDefault()
  dragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) pick(file)
}

onBeforeUnmount(() => {
  if (model.value) URL.revokeObjectURL(src.value)
})
</script>

<template>
  <div
    ref="wrapEl"
    class="cover-pick-wrap"
    @dragover.prevent
    @drop="onDrop"
    @dragenter.prevent="onDragEnter"
    @dragleave="onDragLeave"
  >
    <div v-if="src" class="cover-preview-wrap">
      <img :src="src" alt="封面" class="cover-preview" />
      <div class="cover-overlay">
        <label class="cover-action">
          更换封面
          <input type="file" accept="image/*" @change="onPick" />
        </label>
        <button v-if="removable" class="cover-action" type="button" @click="clear">移除封面</button>
      </div>
    </div>
    <label v-else class="cover-pick-dropzone">
      <input type="file" accept="image/*" @change="onPick" />
      <span class="cover-pick-empty">点击选择或拖拽封面图到此处</span>
    </label>
    <div v-if="dragOver" class="cover-drag-mask">松开以{{ src ? '更换' : '上传' }}封面图</div>
  </div>
</template>

<style scoped>
.cover-pick-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.cover-drag-mask {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--on-primary);
  background: var(--drag-mask-bg);
  border-radius: 10px;
  pointer-events: none;
}

.cover-pick-dropzone {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 6rem;
  padding: 0.5rem;
  border: 2px dashed var(--border-strong);
  border-radius: 10px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.cover-pick-dropzone:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.cover-pick-dropzone input {
  display: none;
}

.cover-pick-empty {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.cover-preview-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
}

.cover-preview {
  max-width: 100%;
  max-height: 8rem;
  object-fit: contain;
  display: block;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  background: var(--overlay-bg);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.cover-preview-wrap:hover .cover-overlay,
.cover-overlay:focus-within {
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
</style>
