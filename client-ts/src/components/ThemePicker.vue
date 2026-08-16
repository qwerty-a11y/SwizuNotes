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
 * 主题选择器：
 *  - 默认形态：调色板圆形按钮 + 下拉面板（桌面导航栏）
 *  - popup 形态：无按钮，居中弹出小窗（Teleport body，移动端侧边栏"主题"入口用）
 * 面板内容：主题列表（当前项勾选高亮）+ 独立开关区（深色模式 / 跟随日期自动切换）。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { getPublicThemes } from '@/api/theme'
import { useThemeStore } from '@/stores/theme'
import {
  LIGHT_THEME_KEY,
  THEME_CHANGE_EVENT,
  getStoredTheme,
  initThemeAuto,
  isDark,
  isThemeAuto,
  selectLightTheme,
  setThemeAuto,
  toggleTheme,
} from '@/theme'
import type { ThemeSummary } from '@/types/theme'

const props = withDefaults(defineProps<{ popup?: boolean }>(), { popup: false })
const emit = defineEmits<{ close: [] }>()

const themeStore = useThemeStore()

const open = ref(false)
const themes = ref<ThemeSummary[]>([])
const auto = ref(isThemeAuto())
const root = ref<HTMLElement | null>(null)

/** 当前主题名（响应式：监听 themechange，主题切换/预览后同步高亮与深浅状态） */
const themeName = ref(getStoredTheme())

/** 当前浅色主题名（深色模式显示最近浅色主题，用于高亮列表项） */
const currentLight = computed(() => {
  const current = themeName.value
  if (isDark(current)) {
    return localStorage.getItem(LIGHT_THEME_KEY) ?? current.replace(/-dark$/, '')
  }
  return current
})

function isCurrent(name: string): boolean {
  // 自动切换选中时，具体主题项不显示选中（避免与自动项同时高亮）
  return !isAutoCurrent.value && currentLight.value === name
}

/** 当前是否为"自动切换"模式（列表高亮用，与 localStorage 同步） */
const isAutoCurrent = computed(() => auto.value)

/** 打开面板时刷新自动跟随状态（导航栏深浅切换/手动选主题会改变 localStorage 中的值） */
function toggleOpen(): void {
  open.value = !open.value
  if (open.value) auto.value = isThemeAuto()
}

/** 选择"自动切换"（列表项）：开启日期自动跟随，应用今天生效的日期主题 */
function chooseAuto(): void {
  themeStore.exitPreview()
  auto.value = true
}

/** 深浅模式切换（圆形图标按钮，独立于主题列表；切换后面板保持打开便于连续调整） */
function onToggleLightDark(): void {
  themeStore.exitPreview()
  themeName.value = toggleTheme()
}

/** 选择具体主题（保持当前深浅模式；手动选择 → 暂停日期自动跟随并退出预览；面板保持打开） */
function choose(name: string): void {
  themeStore.exitPreview()
  selectLightTheme(name)
  themeName.value = getStoredTheme()
  auto.value = false
}

/** 自动跟随开关：开启时立即应用今天生效的日期主题（并退出预览，交还日期主题控制） */
watch(auto, async (value) => {
  setThemeAuto(value)
  if (value) {
    themeStore.exitPreview()
    await initThemeAuto()
  }
})

async function load(): Promise<void> {
  try {
    const res = await getPublicThemes()
    themes.value = res.data
    // 同步已发布主题数（导航栏"主题菜单/深浅按钮"布局依据）
    themeStore.setPublicThemeCount(res.data.length)
  } catch {
    // 网络失败静默（选择器仍可用本地主题）
  }
}

function onDocClick(event: MouseEvent): void {
  if (open.value && root.value && !root.value.contains(event.target as Node)) {
    open.value = false
  }
}

/** 主题切换/预览后同步当前主题名（高亮与深浅状态） */
function onThemeChange(event: Event): void {
  themeName.value = (event as CustomEvent<{ name: string }>).detail?.name ?? getStoredTheme()
}

onMounted(() => {
  void load()
  if (!props.popup) {
    document.addEventListener('click', onDocClick)
  }
  window.addEventListener(THEME_CHANGE_EVENT, onThemeChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener(THEME_CHANGE_EVENT, onThemeChange)
})
</script>

<template>
  <!-- ===== 默认形态：按钮 + 下拉面板 ===== -->
  <div v-if="!popup" ref="root" class="theme-picker">
    <button
      class="theme-palette-btn"
      type="button"
      title="主题"
      aria-label="选择主题"
      :class="{ active: open }"
      @click="toggleOpen"
    >
      <AppIcon name="palette" />
    </button>
    <Transition name="menu">
      <div v-if="open" class="theme-picker-panel">
        <!-- 标题行右侧：圆形深浅模式切换按钮（sun/moon，独立于主题列表） -->
        <div class="theme-picker-head">
          <span>主题</span>
          <button
            class="theme-picker-lightdark"
            type="button"
            :title="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
            :aria-label="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
            @click="onToggleLightDark"
          >
            <AppIcon :name="isDark(themeName) ? 'sun' : 'moon'" />
          </button>
        </div>
        <div class="theme-picker-list">
          <!-- 自动切换作为列表项：选中 = 开启日期自动跟随 -->
          <button
            class="theme-picker-item"
            type="button"
            :class="{ current: isAutoCurrent }"
            @click="chooseAuto"
          >
            <AppIcon v-if="isAutoCurrent" name="check" class="theme-picker-check" />
            <span class="theme-picker-name">自动切换</span>
          </button>
          <template v-if="themes.length === 0">
            <div class="theme-picker-empty">暂无公开主题</div>
          </template>
          <template v-else>
            <button
              v-for="t in themes"
              :key="t.name"
              class="theme-picker-item"
              type="button"
              :class="{ current: isCurrent(t.name) }"
              @click="choose(t.name)"
            >
              <AppIcon v-if="isCurrent(t.name)" name="check" class="theme-picker-check" />
              <span class="theme-picker-name">{{ t.displayName }}</span>
            </button>
          </template>
        </div>
      </div>
    </Transition>
  </div>

  <!-- ===== popup 形态：居中弹出小窗（移动端侧边栏"主题"入口） ===== -->
  <Teleport v-else to="body">
    <div class="theme-popup-mask" @click.self="emit('close')">
      <div class="theme-popup" role="dialog" aria-modal="true">
        <div class="theme-popup-head">
          <span class="theme-popup-title">主题</span>
          <div class="theme-popup-actions">
            <!-- 圆形深浅模式切换按钮（与下拉面板一致，独立于主题列表） -->
            <button
              class="theme-picker-lightdark"
              type="button"
              :title="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
              :aria-label="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
              @click="onToggleLightDark"
            >
              <AppIcon :name="isDark(themeName) ? 'sun' : 'moon'" />
            </button>
            <button class="theme-popup-close" type="button" aria-label="关闭" @click="emit('close')">
              <AppIcon name="close" />
            </button>
          </div>
        </div>
        <div class="theme-picker-list">
          <!-- 自动切换作为列表项 -->
          <button
            class="theme-picker-item"
            type="button"
            :class="{ current: isAutoCurrent }"
            @click="chooseAuto"
          >
            <AppIcon v-if="isAutoCurrent" name="check" class="theme-picker-check" />
            <span class="theme-picker-name">自动切换</span>
          </button>
          <template v-if="themes.length === 0">
            <div class="theme-picker-empty">暂无公开主题</div>
          </template>
          <template v-else>
            <button
              v-for="t in themes"
              :key="t.name"
              class="theme-picker-item"
              type="button"
              :class="{ current: isCurrent(t.name) }"
              @click="choose(t.name)"
            >
              <AppIcon v-if="isCurrent(t.name)" name="check" class="theme-picker-check" />
              <span class="theme-picker-name">{{ t.displayName }}</span>
            </button>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.theme-picker {
  position: relative;
  flex-shrink: 0;
}

.theme-palette-btn {
  width: 2.2rem;
  height: 2.2rem;
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
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.theme-palette-btn:hover,
.theme-palette-btn.active {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.theme-palette-btn svg {
  width: 1.15rem;
  height: 1.15rem;
  fill: currentColor;
}

.theme-picker-panel {
  position: absolute;
  top: calc(100% + 0.6rem);
  right: 0;
  z-index: 200;
  min-width: 13rem;
  padding: 0.4rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-lift);
}

.theme-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.45rem 0.5rem 0.4rem 0.7rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-faint);
  border-bottom: 1px solid var(--border);
  margin-bottom: 0.3rem;
}

/* 圆形深浅模式切换按钮（与导航栏 theme-toggle 同款视觉，sun/moon） */
.theme-picker-lightdark {
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
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.theme-picker-lightdark:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.theme-picker-lightdark svg {
  width: 1.05rem;
  height: 1.05rem;
  fill: currentColor;
}

.theme-picker-empty {
  padding: 0.5rem 0.7rem;
  font-size: 0.85rem;
  color: var(--text-faint);
}

.theme-picker-list {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.theme-picker-item {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  padding: 0.5rem 0.7rem;
  font-family: inherit;
  font-size: 0.85rem;
  color: var(--text);
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease;
}

.theme-picker-item:hover {
  background: var(--primary-soft);
  color: var(--primary);
}

.theme-picker-item.current {
  color: var(--primary);
}

.theme-picker-check {
  flex-shrink: 0;
  width: 1rem;
  height: 1rem;
  fill: currentColor;
}

.theme-picker-item:not(.current) .theme-picker-check {
  visibility: hidden;
}

.theme-picker-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 下拉动画（与账户菜单一致） */
.menu-enter-active,
.menu-leave-active {
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ===== popup 弹层（移动端） ===== */
.theme-popup-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: var(--overlay-bg);
}

.theme-popup {
  width: min(20rem, 100%);
  padding: 0.6rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-lift);
}

.theme-popup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.4rem 0.5rem 0.5rem;
  border-bottom: 1px solid var(--border);
  margin-bottom: 0.4rem;
}

.theme-popup-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-strong);
}

.theme-popup-actions {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.theme-popup-close {
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: var(--text-muted);
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.theme-popup-close:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

.theme-popup-close svg {
  width: 1.2rem;
  height: 1.2rem;
  fill: currentColor;
}
</style>
