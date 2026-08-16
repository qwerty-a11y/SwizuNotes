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
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

const props = defineProps<{
  /** 待裁剪图片 URL */
  src: string
}>()

const emit = defineEmits<{
  close: []
  cropped: [blob: Blob]
}>()

const imgEl = ref<HTMLImageElement | null>(null)
let cropper: Cropper | null = null

onMounted(() => {
  void nextTick(() => {
    if (imgEl.value) {
      cropper = new Cropper(imgEl.value, {
        aspectRatio: 1,
        viewMode: 1,
        autoCropArea: 1,
        background: true,
      })
    }
  })
})

function confirmCrop(): void {
  const canvas = cropper?.getCroppedCanvas({
    width: 512,
    height: 512,
    imageSmoothingQuality: 'high',
  })
  if (!canvas) return
  canvas.toBlob((blob) => {
    if (blob) emit('cropped', blob)
    emit('close')
  }, 'image/png')
}

onBeforeUnmount(() => {
  cropper?.destroy()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog" appear>
      <div class="crop-mask" @click.self="emit('close')">
        <div class="crop-dialog">
          <h3 class="crop-title">裁剪头像（1:1）</h3>
          <div class="crop-stage">
            <img ref="imgEl" :src="props.src" alt="待裁剪图片" />
          </div>
          <div class="crop-actions">
            <button class="crop-btn" type="button" @click="emit('close')">取消</button>
            <button class="crop-btn primary" type="button" @click="confirmCrop">确认裁剪</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.crop-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-bg);
  backdrop-filter: blur(6px);
}

.crop-dialog {
  width: min(30rem, calc(100vw - 2rem));
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1.5rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-lift);
}

.crop-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.crop-stage {
  min-height: 0;
  overflow: hidden;
  border-radius: 10px;
  background: var(--bg-muted);
}

.crop-stage :deep(.cropper-container) {
  width: 100%;
}

.crop-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.crop-btn {
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

.crop-btn:hover {
  background: var(--bg-muted);
}

.crop-btn.primary {
  color: var(--on-primary);
  background: var(--primary);
  border-color: var(--primary);
}

.crop-btn.primary:hover {
  background: var(--primary-hover);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.2s ease;
}

.dialog-enter-active .crop-dialog,
.dialog-leave-active .crop-dialog {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .crop-dialog,
.dialog-leave-to .crop-dialog {
  opacity: 0;
  transform: scale(0.95) translateY(12px);
}
</style>
