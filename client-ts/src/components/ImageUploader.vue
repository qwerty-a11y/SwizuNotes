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
import { useMediaUpload } from '@/composables/useMediaUpload'
import type { MediaCategory, MediaResponse } from '@/types/media'

const props = defineProps<{
  articleId: number | string
  fileType?: MediaCategory
}>()

const emit = defineEmits<{
  uploaded: [media: MediaResponse]
}>()

const { uploading, error, upload } = useMediaUpload()
const preview = ref('')

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  preview.value = URL.createObjectURL(file)
  upload(file, Number(props.articleId), props.fileType ?? 'image')
    .then((media) => emit('uploaded', media))
    .catch(() => {})
    .finally(() => {
      URL.revokeObjectURL(preview.value)
      input.value = ''
    })
}
</script>

<template>
  <div class="uploader">
    <label class="pick">
      选择图片上传
      <input type="file" accept="image/*" @change="onFileChange" :disabled="uploading" />
    </label>
    <p v-if="uploading">上传中…</p>
    <p v-if="error" class="error">{{ error }}</p>
    <img v-if="preview" class="preview" :src="preview" alt="预览" />
  </div>
</template>

<style scoped>
.pick {
  display: inline-block;
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.pick input {
  display: none;
}

.preview {
  display: block;
  max-width: 100%;
  margin-top: 0.5rem;
}

.error {
  color: #c0392b;
}
</style>
