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
/**
 * 全局顶部 toast 通知容器（App.vue 挂载，Teleport 到 body）：
 * 渲染 utils/toast.ts 的响应式列表，INFO（主题色）/ WARN（警告色）/ ERROR（危险色）
 * 三等级，左侧色条 + 底色微染 + 等级图标；自动消失（utils/toast.ts 定时），点击可关闭。
 */
import AppIcon from '@/components/AppIcon.vue'
import { useToasts } from '@/utils/toast'

const { toasts, dismiss } = useToasts()

const LEVEL_ICON: Record<string, 'info' | 'warn' | 'error'> = {
  info: 'info',
  warn: 'warn',
  error: 'error',
}
</script>

<template>
  <Teleport to="body">
    <div class="toast-host" aria-live="polite">
      <TransitionGroup name="toast">
        <div
          v-for="t in toasts"
          :key="t.id"
          class="toast"
          :class="`toast-${t.level}`"
          role="status"
          @click="dismiss(t.id)"
        >
          <AppIcon :name="LEVEL_ICON[t.level]" class="toast-icon" />
          <span class="toast-message">{{ t.message }}</span>
          <AppIcon name="close" class="toast-close" />
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
/* 顶部居中容器：空白区域不拦截点击（pointer-events: none），toast 自身可点 */
.toast-host {
  position: fixed;
  top: 0.9rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  max-width: min(30rem, calc(100vw - 2rem));
  padding: 0.55rem 0.9rem;
  font-size: 0.88rem;
  color: var(--text);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-left: 3px solid var(--toast-color);
  border-radius: 10px;
  box-shadow: var(--shadow-lift);
  cursor: pointer;
}

/* 三等级配色：左色条 + 底色微染（toast 悬停不动） */
.toast-info {
  --toast-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 6%, var(--bg-card));
}

.toast-warn {
  --toast-color: var(--warning);
  background: color-mix(in srgb, var(--warning) 10%, var(--bg-card));
}

.toast-error {
  --toast-color: var(--danger);
  background: color-mix(in srgb, var(--danger) 8%, var(--bg-card));
}

.toast-icon {
  flex-shrink: 0;
  width: 1.05rem;
  height: 1.05rem;
  fill: var(--toast-color);
}

.toast-message {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-close {
  flex-shrink: 0;
  width: 0.9rem;
  height: 0.9rem;
  fill: var(--text-faint);
}

/* 进入/离开动画：顶部下滑淡入，离开上滑淡出 */
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-0.6rem);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-0.6rem);
}

.toast-move {
  transition: transform 0.2s ease;
}
</style>
