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
import AppIcon from '@/components/AppIcon.vue'
import NavBar from '@/components/NavBar.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useThemeStore } from '@/stores/theme'
import { toast } from '@/utils/toast'

const themeStore = useThemeStore()

/** 预览深浅切换（浮条 sun/moon 按钮；失败保持原预览并提示） */
function onTogglePreviewDark(): void {
  themeStore.togglePreviewDark().catch((e) => toast.error((e as Error).message))
}
</script>

<template>
  <NavBar />
  <main class="app-main">
    <RouterView />
  </main>
  <!-- 全局顶部 toast 通知（INFO/WARN/ERROR 三等级） -->
  <ToastHost />
  <!-- 全局预览浮条（预览跨路由持久，任何页面可见可退出；含深浅切换） -->
  <div v-if="themeStore.preview" class="preview-float">
    <span class="preview-float-text">预览中：{{ themeStore.preview.displayName }}</span>
    <button
      type="button"
      class="preview-float-dark"
      :title="themeStore.preview.dark ? '切换到浅色' : '切换到深色'"
      :aria-label="themeStore.preview.dark ? '切换到浅色' : '切换到深色'"
      @click="onTogglePreviewDark"
    >
      <AppIcon :name="themeStore.preview.dark ? 'sun' : 'moon'" />
    </button>
    <button type="button" class="preview-float-exit" @click="themeStore.exitPreview()">退出预览</button>
  </div>
</template>

<style scoped>
/* 预览浮条：固定在底部中央，高于导航栏与悬浮窗 */
.preview-float {
  position: fixed;
  left: 50%;
  bottom: 1.2rem;
  transform: translateX(-50%);
  z-index: 500;
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.55rem 0.9rem 0.55rem 1.1rem;
  background: color-mix(in srgb, var(--primary) 14%, var(--bg-card));
  border: 1px solid var(--primary-soft-border);
  border-radius: 999px;
  box-shadow: var(--shadow-lift);
  font-size: 0.88rem;
  color: var(--text);
  backdrop-filter: blur(8px) saturate(180%);
}

.preview-float-exit {
  padding: 0.3rem 0.9rem;
  font-family: inherit;
  font-size: 0.82rem;
  color: var(--on-primary);
  background: var(--primary);
  border: none;
  border-radius: 999px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease;
}

.preview-float-exit:hover {
  background: var(--primary-hover);
}

/* 预览深浅切换（圆形 sun/moon，与导航栏同款视觉） */
.preview-float-dark {
  width: 2rem;
  height: 2rem;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text);
  background: none;
  border: 1px solid color-mix(in srgb, var(--primary) 40%, transparent);
  border-radius: 50%;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.preview-float-dark:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.preview-float-dark svg {
  width: 1.05rem;
  height: 1.05rem;
  fill: currentColor;
}
</style>
