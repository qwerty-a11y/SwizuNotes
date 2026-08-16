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
import AppIcon from './AppIcon.vue'

/**
 * 搜索输入框（亚克力风格）：v-model 绑定关键词，
 * 左侧放大镜图标 + 右侧有内容时显示清除按钮。
 */
defineProps<{ placeholder?: string }>()
const model = defineModel<string>({ default: '' })
</script>

<template>
  <div class="search-box">
    <AppIcon name="search" class="search-icon" />
    <input v-model="model" type="search" :placeholder="placeholder || '搜索…'" />
    <button
      v-if="model"
      class="search-clear"
      type="button"
      aria-label="清除搜索"
      @click="model = ''"
    >
      <AppIcon name="close" />
    </button>
  </div>
</template>

<style scoped>
.search-box {
  display: flex;
  align-items: center;
  background: color-mix(in srgb, var(--bg-card) 75%, transparent);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: border-color 0.2s ease;
}

.search-box:focus-within {
  border-color: var(--primary);
}

.search-icon {
  flex-shrink: 0;
  margin-left: 0.75rem;
  width: 1.1rem;
  height: 1.1rem;
  color: var(--text-faint);
}

.search-box input {
  flex: 1;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  background: none;
  border: none;
  outline: none;
  color: var(--text);
  font-size: 0.95rem;
  font-family: inherit;
}

.search-box input::placeholder {
  color: var(--text-faint);
}

.search-clear {
  flex-shrink: 0;
  margin-right: 0.5rem;
  width: 1.5rem;
  height: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: none;
  border: none;
  border-radius: 50%;
  color: var(--text-faint);
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.search-clear:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

.search-clear svg {
  width: 0.9rem;
  height: 0.9rem;
  fill: currentColor;
}
</style>
