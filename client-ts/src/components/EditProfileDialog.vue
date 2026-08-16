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
import { avatarUrl, updateUsername, uploadAvatar } from '@/api/user'
import { useUserStore } from '@/stores/user'
import AvatarCropper from '@/components/AvatarCropper.vue'

const props = defineProps<{
  /** 当前用户 id（显示当前头像用） */
  userId: number
  /** 初始昵称 */
  initialName: string
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const userStore = useUserStore()

const editName = ref(props.initialName)
const editAvatarBlob = ref<Blob | null>(null)
/** 新选头像的预览 URL（裁剪后） */
const editAvatarPreview = ref('')
/** 待裁剪图片 URL（非空时打开裁剪弹窗） */
const cropSrc = ref('')
const editSaving = ref(false)
const editError = ref('')
const avatarBroken = ref(false)

function close(): void {
  if (editAvatarPreview.value) URL.revokeObjectURL(editAvatarPreview.value)
  emit('close')
}

/** 选择头像图片 → 打开 1:1 裁剪弹窗 */
function onAvatarPick(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !file.type.startsWith('image/')) return
  if (editAvatarPreview.value) URL.revokeObjectURL(editAvatarPreview.value)
  editAvatarPreview.value = ''
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropSrc.value = URL.createObjectURL(file)
}

function onAvatarDrop(event: DragEvent): void {
  event.preventDefault()
  const file = event.dataTransfer?.files?.[0]
  if (!file || !file.type.startsWith('image/')) return
  if (editAvatarPreview.value) URL.revokeObjectURL(editAvatarPreview.value)
  editAvatarPreview.value = ''
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
  cropSrc.value = URL.createObjectURL(file)
}

function onAvatarCropped(blob: Blob): void {
  if (editAvatarPreview.value) URL.revokeObjectURL(editAvatarPreview.value)
  editAvatarBlob.value = blob
  editAvatarPreview.value = URL.createObjectURL(blob)
}

async function save(): Promise<void> {
  if (editSaving.value) return
  const name = editName.value.trim()
  if (!name) {
    editError.value = '昵称不能为空'
    return
  }
  editSaving.value = true
  editError.value = ''
  try {
    if (editAvatarBlob.value) {
      await uploadAvatar(editAvatarBlob.value)
      avatarBroken.value = false
      userStore.bumpAvatar()
    }
    await updateUsername(name)
    // 同步导航栏菜单昵称
    await userStore.ensureUserId(true)
    emit('saved')
  } catch (e) {
    editError.value = (e as Error).message
  } finally {
    editSaving.value = false
  }
}

onBeforeUnmount(() => {
  if (editAvatarPreview.value) URL.revokeObjectURL(editAvatarPreview.value)
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog" appear>
      <div class="edit-mask" @click.self="close">
        <div class="edit-dialog">
          <h3 class="edit-title">编辑资料</h3>
          <div class="edit-field">
            <label>昵称</label>
            <input v-model="editName" maxlength="50" placeholder="昵称" />
          </div>
          <div class="edit-field">
            <label>头像</label>
            <div class="avatar-edit-wrap" @dragover.prevent @drop="onAvatarDrop">
              <img v-if="editAvatarPreview" :src="editAvatarPreview" alt="新头像预览" class="avatar-edit-img" />
              <img
                v-else-if="!avatarBroken"
                :src="`${avatarUrl(userId)}?v=${userStore.avatarVersion}`"
                alt="当前头像"
                class="avatar-edit-img"
                @error="avatarBroken = true"
              />
              <span v-else class="avatar-edit-empty">未设置头像</span>
              <div class="avatar-edit-overlay">
                <label class="avatar-change-btn">
                  更换头像
                  <input type="file" accept="image/*" @change="onAvatarPick" />
                </label>
              </div>
            </div>
          </div>
          <p v-if="editError" class="edit-error">{{ editError }}</p>
          <div class="edit-actions">
            <button class="edit-btn" type="button" @click="close">取消</button>
            <button class="edit-btn primary" type="button" :disabled="editSaving" @click="save">
              {{ editSaving ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <AvatarCropper v-if="cropSrc" :src="cropSrc" @close="cropSrc = ''" @cropped="onAvatarCropped" />
</template>

<style scoped>
.edit-mask {
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

.edit-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
}

.edit-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.edit-field > label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
}

.edit-field input {
  padding: 0.5rem 0.7rem;
  font-family: inherit;
  font-size: 0.9rem;
  color: var(--text-strong);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  outline: none;
}

.edit-field input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}

.edit-error {
  margin: 0;
  font-size: 0.85rem;
  color: var(--danger);
}

.avatar-edit-wrap {
  position: relative;
  width: 8rem;
  height: 8rem;
  margin: 0 auto;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--bg-muted);
}

.avatar-edit-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-edit-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  color: var(--text-faint);
}

.avatar-edit-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-bg);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-edit-wrap:hover .avatar-edit-overlay,
.avatar-edit-overlay:focus-within {
  opacity: 1;
}

.avatar-change-btn {
  padding: 0.35rem 0.9rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--overlay-btn-text);
  background: var(--overlay-btn-bg);
  border: 1px solid var(--overlay-btn-border);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.avatar-change-btn:hover {
  background: var(--overlay-btn-bg-hover);
}

.avatar-change-btn input {
  display: none;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.edit-btn {
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

.edit-btn:hover {
  background: var(--bg-muted);
}

.edit-btn.primary {
  color: var(--on-primary);
  background: var(--primary);
  border-color: var(--primary);
}

.edit-btn.primary:hover {
  background: var(--primary-hover);
}

.edit-btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
</style>
