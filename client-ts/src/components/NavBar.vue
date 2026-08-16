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
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { avatarErrorFallback, avatarUrl } from '@/api/user'
import { getPublicThemes } from '@/api/theme'
import AppIcon from '@/components/AppIcon.vue'
import ArticleSearch from '@/components/ArticleSearch.vue'
import ThemePicker from '@/components/ThemePicker.vue'
import { getStoredTheme, isDark, setThemeAuto, THEME_CHANGE_EVENT, toggleTheme } from '@/theme'

const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const themeName = ref(getStoredTheme())

/**
 * 导航栏主题布局由**已发布主题数量**决定（themeStore.publicThemeCount）：
 *  - 已发布主题数 ≤ 1：只显示深浅按钮，主题菜单隐藏（唯一主题无可切换，
 *    主题固定为跟随日期自动切换，深浅切换不暂停自动跟随）
 *  - 已发布主题数 > 1：隐藏深浅按钮，显示主题菜单（深浅切换集成在菜单内）
 */

/** 深浅色切换：深色显示太阳（点击回浅色），浅色显示月亮（点击进深色） */
function onToggleTheme(): void {
  // 手动切换主题 = 退出预览模式
  themeStore.exitPreview()
  // 已发布主题数 ≤ 1 时主题固定为自动切换：深浅切换不暂停日期跟随
  themeName.value = toggleTheme(themeStore.publicThemeCount <= 1)
}

const menuOpen = ref(false)
const menuWrap = ref<HTMLElement | null>(null)

/** 移动端侧边栏 */
const sidebarOpen = ref(false)
/** 移动端主题弹出小窗（侧边栏"主题"入口触发） */
const sidebarThemeOpen = ref(false)

/** 打开主题小窗（同时收起侧边栏，弹层全屏覆盖更干净） */
function openThemePopup(): void {
  sidebarOpen.value = false
  sidebarThemeOpen.value = true
}

/** 移动端搜索条展开态 */
const searchExpanded = ref(false)
const navSearch = ref<InstanceType<typeof ArticleSearch> | null>(null)
const mobileSearch = ref<InstanceType<typeof ArticleSearch> | null>(null)

function openSearchBar(): void {
  searchExpanded.value = true
  // 等搜索条渲染后再聚焦输入框
  requestAnimationFrame(() => mobileSearch.value?.focus())
}

function closeSearchBar(): void {
  searchExpanded.value = false
}

/** 路由跳转后收起移动端搜索条 */
watch(
  () => router.currentRoute.value.path,
  () => closeSearchBar(),
)

watch(sidebarOpen, (open) => {
  // 打开侧边栏时锁定页面滚动
  document.body.style.overflow = open ? 'hidden' : ''
})

function closeSidebar(): void {
  sidebarOpen.value = false
}

onMounted(() => {
  // 强制校验本地 userId（可能过期/为 0），保证导航栏"账号 → 本人主页"链接正确
  void userStore.ensureUserId(true)
  document.addEventListener('click', onDocClick)
  // 主题变化（含预览深浅切换/退出预览派发的 themechange）：同步 sun/moon 图标
  window.addEventListener(THEME_CHANGE_EVENT, onThemeChange)
  // 拉取已发布主题数（决定主题菜单/深浅按钮布局；ThemePicker 加载时也会刷新）
  getPublicThemes()
    .then((res) => {
      themeStore.setPublicThemeCount(res.data.length)
      // 已发布主题数 ≤ 1 时主题固定为跟随日期自动切换（无可切换主题，恢复/保持自动跟随）
      if (res.data.length <= 1) setThemeAuto(true)
    })
    .catch(() => {
      // 网络失败保持 0（只显示深浅按钮）
    })
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener(THEME_CHANGE_EVENT, onThemeChange)
  document.body.style.overflow = ''
})

/** themechange 事件：同步导航栏深浅按钮图标（预览切换/退出预览等非导航栏路径的主题变化） */
function onThemeChange(event: Event): void {
  const name = (event as CustomEvent<{ name?: string }>).detail?.name
  themeName.value = name ?? getStoredTheme()
}

/** 点击菜单外部区域时收起 */
function onDocClick(event: MouseEvent): void {
  if (menuOpen.value && menuWrap.value && !menuWrap.value.contains(event.target as Node)) {
    menuOpen.value = false
  }
}

function logout(): void {
  menuOpen.value = false
  closeSidebar()
  userStore.logout()
  router.push('/login')
}

function goProfile(): void {
  menuOpen.value = false
  closeSidebar()
  router.push(`/user/${userStore.userId}`)
}

/** 已在编辑页时点"写文章"不做任何操作（避免触发离开确认提示/URL 变化） */
function goWrite(): void {
  if (router.currentRoute.value.path.startsWith('/editor')) return
  closeSidebar()
  router.push('/editor')
}

/** 侧边栏内路由跳转后收起（RouterLink 场景） */
function closeAfterNavigate(): void {
  closeSidebar()
}
</script>

<template>
  <header class="app-header">
    <button class="menu-btn" type="button" aria-label="打开菜单" title="菜单" @click="sidebarOpen = true">
      <AppIcon name="menu" />
    </button>
    <RouterLink to="/" class="brand">
      <img src="/logo.svg" alt="SwizuNotes" class="brand-logo" />
      <span>SwizuNotes</span>
    </RouterLink>
    <!-- 桌面端搜索框（导航栏中间部分） -->
    <ArticleSearch ref="navSearch" class="nav-search" />
    <nav class="nav">
      <RouterLink to="/" class="nav-link">
        <AppIcon name="home" />
        <span>首页</span>
      </RouterLink>
      <button v-if="userStore.isLoggedIn" class="nav-link" type="button" @click="goWrite">
        <AppIcon name="write" />
        <span>写文章</span>
      </button>
      <RouterLink v-if="userStore.isLoggedIn && userStore.isAdmin" to="/admin" class="nav-link">
        <AppIcon name="settings" />
        <span>管理</span>
      </RouterLink>
      <!-- 登录后：已发布主题数 ≤ 1 时只显示深浅按钮；> 1 时换成主题菜单（深浅集成在菜单内） -->
      <button
        v-if="userStore.isLoggedIn && themeStore.publicThemeCount <= 1"
        class="theme-toggle"
        type="button"
        :title="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
        :aria-label="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
        @click="onToggleTheme"
      >
        <AppIcon :name="isDark(themeName) ? 'sun' : 'moon'" />
      </button>
      <ThemePicker v-if="userStore.isLoggedIn && themeStore.publicThemeCount > 1" class="theme-picker-nav" />
      <div v-if="userStore.isLoggedIn" ref="menuWrap" class="user-menu">
        <button class="avatar-btn" type="button" title="账户菜单" @click.stop="menuOpen = !menuOpen">
          <img
            :src="`${avatarUrl(userStore.userId)}?v=${userStore.avatarVersion}`"
            alt="头像"
            @error="avatarErrorFallback"
          />
        </button>
        <Transition name="menu">
          <div v-if="menuOpen" class="user-menu-panel">
            <div class="user-menu-head">
              <span class="user-menu-name">{{ userStore.username || userStore.account }}</span>
              <span class="user-menu-account">@{{ userStore.account }}</span>
            </div>
            <button class="user-menu-item" type="button" @click="goProfile">
              <AppIcon name="person" />
              个人中心
            </button>
            <button class="user-menu-item" type="button" @click="logout">
              <AppIcon name="logout" />
              退出登录
            </button>
          </div>
        </Transition>
      </div>
      <template v-else>
        <RouterLink to="/login" class="nav-link">
          <AppIcon name="login" />
          <span>登录</span>
        </RouterLink>
        <!-- 未登录：已发布主题数 ≤ 1 时只显示深浅按钮；> 1 时换成主题菜单（深浅集成在菜单内） -->
        <button
          v-if="themeStore.publicThemeCount <= 1"
          class="theme-toggle"
          type="button"
          :title="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
          :aria-label="isDark(themeName) ? '切换到浅色模式' : '切换到深色模式'"
          @click="onToggleTheme"
        >
          <AppIcon :name="isDark(themeName) ? 'sun' : 'moon'" />
        </button>
        <ThemePicker v-if="themeStore.publicThemeCount > 1" class="theme-picker-nav" />
      </template>
    </nav>
    <!-- 移动端搜索按钮（导航栏右侧） -->
    <button class="search-btn" type="button" aria-label="搜索" title="搜索" @click="openSearchBar">
      <AppIcon name="search" />
    </button>
    <!-- 移动端展开的搜索条（header 下方全宽） -->
    <Transition name="search-bar">
      <div v-if="searchExpanded" class="mobile-search-bar">
        <ArticleSearch ref="mobileSearch" />
        <button class="mobile-search-close" type="button" aria-label="关闭搜索" @click="closeSearchBar">
          <AppIcon name="close" />
        </button>
      </div>
    </Transition>
  </header>

  <!-- 移动端侧边栏（Teleport 到 body，避免 header 的 backdrop-filter 影响 fixed 定位） -->
  <Teleport to="body">
    <Transition name="sidebar">
      <div v-if="sidebarOpen" class="sidebar-mask" @click.self="closeSidebar">
        <aside class="sidebar">
          <div class="sidebar-head">
            <span class="sidebar-brand">SwizuNotes</span>
            <button class="sidebar-close" type="button" aria-label="关闭菜单" @click="closeSidebar">
              <AppIcon name="close" />
            </button>
          </div>

          <!-- 用户区：信息行整体可点击进入个人中心 -->
          <div class="sidebar-section sidebar-user-section">
            <button v-if="userStore.isLoggedIn" class="sidebar-user" type="button" @click="goProfile">
              <img
                :src="`${avatarUrl(userStore.userId)}?v=${userStore.avatarVersion}`"
                alt="头像"
                class="sidebar-avatar"
                @error="avatarErrorFallback"
              />
              <span class="sidebar-user-info">
                <span class="sidebar-user-name">{{ userStore.username || userStore.account }}</span>
                <span class="sidebar-user-account">@{{ userStore.account }}</span>
              </span>
              <AppIcon name="chevron" class="sidebar-user-arrow" />
            </button>
            <RouterLink v-else to="/login" class="sidebar-link" @click="closeAfterNavigate">
              <AppIcon name="login" />
              <span>登录</span>
            </RouterLink>
          </div>

          <nav class="sidebar-nav">
            <RouterLink to="/" class="sidebar-link" @click="closeAfterNavigate">
              <AppIcon name="home" />
              <span>首页</span>
            </RouterLink>
            <button v-if="userStore.isLoggedIn" class="sidebar-link" type="button" @click="goWrite">
              <AppIcon name="write" />
              <span>写文章</span>
            </button>
            <RouterLink v-if="userStore.isLoggedIn && userStore.isAdmin" to="/admin" class="sidebar-link" @click="closeAfterNavigate">
              <AppIcon name="settings" />
              <span>管理</span>
            </RouterLink>
          </nav>

          <!-- 底部固定：主题入口 + 退出登录 -->
          <div class="sidebar-footer">
            <!-- 已发布主题数 ≤ 1：深浅切换行；> 1："主题"行点击弹出小窗（不直接平铺列表） -->
            <button v-if="themeStore.publicThemeCount <= 1" class="sidebar-link" type="button" @click="onToggleTheme">
              <AppIcon :name="isDark(themeName) ? 'sun' : 'moon'" />
              <span>{{ isDark(themeName) ? '浅色模式' : '深色模式' }}</span>
            </button>
            <button v-else class="sidebar-link" type="button" @click="openThemePopup">
              <AppIcon name="palette" />
              <span>主题</span>
            </button>
            <button v-if="userStore.isLoggedIn" class="sidebar-link sidebar-link-danger" type="button" @click="logout">
              <AppIcon name="logout" />
              <span>退出登录</span>
            </button>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>

  <!-- 移动端主题弹出小窗（侧边栏"主题"入口触发，Teleport body） -->
  <ThemePicker v-if="sidebarThemeOpen" popup @close="sidebarThemeOpen = false" />
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid var(--border);
  background: var(--bg-header);
  backdrop-filter: blur(8px) saturate(180%);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.25rem;
  font-weight: bold;
  text-decoration: none;
  color: inherit;
  flex-shrink: 0;
}

.brand-logo {
  width: 2rem;
  height: 2rem;
}

.nav {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  /* 不参与收缩：窗口变窄时由搜索框承担压缩 */
  flex-shrink: 0;
}

/* 桌面端导航栏中间搜索框：弹性宽度（22rem 首选，随窗口在 8-30rem 间伸缩），
   grow 填充中间剩余空间（max-width 封顶后由 space-between 居中），
   两侧组件（brand/nav）不收缩，窗口变窄时只压缩搜索框 */
.nav-search {
  flex: 1 1 22rem;
  min-width: 8rem;
  max-width: 30rem;
  margin: 0 1.5rem;
}

.nav a,
.nav-link {
  color: inherit;
  text-decoration: none;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: inherit;
  font-size: 1rem;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.nav-link svg {
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
  fill: currentColor;
}

.nav a:hover,
.nav-link:hover {
  color: var(--primary);
}

.account {
  color: var(--primary);
  font-size: 0.9rem;
  font-weight: 600;
}

.theme-toggle {
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

.theme-toggle:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.theme-toggle svg {
  width: 1.15rem;
  height: 1.15rem;
  fill: currentColor;
}

.user-menu {
  position: relative;
}

.avatar-btn {
  width: 2.2rem;
  height: 2.2rem;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--primary-soft);
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s ease;
}

.avatar-btn:hover {
  border-color: var(--primary);
}

.avatar-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-menu-panel {
  position: absolute;
  top: calc(100% + 0.6rem);
  right: 0;
  z-index: 200;
  min-width: 12rem;
  padding: 0.4rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-lift);
}

.user-menu-head {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.55rem 0.7rem 0.6rem;
  border-bottom: 1px solid var(--border);
  margin-bottom: 0.3rem;
}

.user-menu-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-strong);
}

.user-menu-account {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.user-menu-item {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  padding: 0.5rem 0.7rem;
  font-size: 0.85rem;
  color: var(--text);
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-decoration: none;
  text-align: left;
  transition: background 0.15s ease;
}

.user-menu-item:hover {
  background: var(--primary-soft);
  color: var(--primary);
}

.user-menu-item svg {
  flex-shrink: 0;
  width: 1.05rem;
  height: 1.05rem;
  fill: currentColor;
}

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

/* ===== 移动端汉堡按钮 ===== */

.menu-btn {
  display: none;
  width: 2.2rem;
  height: 2.2rem;
  padding: 0;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  background: none;
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.menu-btn:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.menu-btn svg {
  width: 1.3rem;
  height: 1.3rem;
  fill: currentColor;
}

/* ===== 移动端搜索按钮（默认隐藏，≤768px 显示在导航栏右侧） ===== */

.search-btn {
  display: none;
  width: 2.2rem;
  height: 2.2rem;
  padding: 0;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  color: var(--text);
  background: none;
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.search-btn:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.search-btn svg {
  width: 1.2rem;
  height: 1.2rem;
  fill: currentColor;
}

/* ===== 移动端展开的搜索条（header 下方全宽） ===== */

.mobile-search-bar {
  display: none;
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  gap: 0.6rem;
  padding: 0.6rem 1rem;
  align-items: center;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(8px) saturate(180%);
}

.mobile-search-bar .article-search {
  flex: 1;
  min-width: 0;
}

.mobile-search-close {
  width: 2.2rem;
  height: 2.2rem;
  padding: 0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  background: none;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.mobile-search-close:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

.mobile-search-close svg {
  width: 1.2rem;
  height: 1.2rem;
  fill: currentColor;
}

/* 搜索条展开/收起动画 */
.search-bar-enter-active,
.search-bar-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.search-bar-enter-from,
.search-bar-leave-to {
  opacity: 0;
  transform: translateY(-0.5rem);
}

/* ===== 移动端侧边栏 ===== */

.sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: var(--overlay-bg);
}

.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 1001;
  width: 18rem;
  max-width: 85vw;
  display: flex;
  flex-direction: column;
  padding: 1rem 0.75rem;
  background: var(--bg-card);
  border-right: 1px solid var(--border);
  box-shadow: var(--shadow-lift);
  overflow-y: auto;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.25rem 0.25rem 0.75rem;
  border-bottom: 1px solid var(--border);
  margin-bottom: 0.75rem;
}

.sidebar-brand {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-strong);
}

.sidebar-close {
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

.sidebar-close:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

.sidebar-close svg {
  width: 1.2rem;
  height: 1.2rem;
  fill: currentColor;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

/* 用户区：信息行整体可点击（进入个人中心） */
.sidebar-user {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.5rem 0.7rem;
  font-family: inherit;
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease;
}

.sidebar-user:hover {
  background: var(--primary-soft);
}

.sidebar-user-arrow {
  flex-shrink: 0;
  width: 1.2rem;
  height: 1.2rem;
  margin-left: auto;
  color: var(--text-faint);
}

.sidebar-user:hover .sidebar-user-arrow {
  color: var(--primary);
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  padding: 0.6rem 0.7rem;
  font-family: inherit;
  font-size: 0.95rem;
  color: var(--text);
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-decoration: none;
  text-align: left;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.sidebar-link:hover {
  background: var(--primary-soft);
  color: var(--primary);
}

.sidebar-link svg {
  flex-shrink: 0;
  width: 1.15rem;
  height: 1.15rem;
  fill: currentColor;
}

.sidebar-link-danger {
  color: var(--danger);
}

.sidebar-link-danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

/* 底部固定区（主题入口 + 退出登录）：沉底 */
.sidebar-footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.sidebar-avatar {
  flex-shrink: 0;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 50%;
  object-fit: cover;
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}

.sidebar-user-info {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.sidebar-user-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-user-account {
  font-size: 0.75rem;
  color: var(--text-faint);
}

/* 侧边栏滑入/滑出动画（mask 渐隐 + 面板平移） */
.sidebar-enter-active,
.sidebar-leave-active {
  transition: opacity 0.25s ease;
}

.sidebar-enter-active .sidebar,
.sidebar-leave-active .sidebar {
  transition: transform 0.25s ease;
}

.sidebar-enter-from,
.sidebar-leave-to {
  opacity: 0;
}

.sidebar-enter-from .sidebar,
.sidebar-leave-to .sidebar {
  transform: translateX(-100%);
}

/* ===== 响应式：≤768px 折叠导航到侧边栏 ===== */

@media (max-width: 768px) {
  .nav-search {
    display: none;
  }

  .menu-btn {
    display: flex;
  }

  .search-btn {
    display: flex;
    margin-left: auto;
  }

  .mobile-search-bar {
    display: flex;
  }

  .nav {
    display: none;
  }

  .app-header {
    justify-content: flex-start;
    gap: 0.75rem;
    padding: 0.6rem 1rem;
  }
}
</style>
