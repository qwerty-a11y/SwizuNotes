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
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function logout(): void {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <RouterLink to="/" class="brand">
      <img src="/logo.svg" alt="SwizuNotes" class="brand-logo" />
      <span>SwizuNotes</span>
    </RouterLink>
    <nav class="nav">
      <RouterLink to="/">首页</RouterLink>
      <RouterLink v-if="userStore.isLoggedIn" to="/editor">写文章</RouterLink>
      <RouterLink v-if="userStore.isLoggedIn" to="/admin">管理</RouterLink>
      <template v-if="userStore.isLoggedIn">
        <span class="account">{{ userStore.account }}</span>
        <button class="logout" @click="logout">退出</button>
      </template>
      <RouterLink v-else to="/login">登录</RouterLink>
    </nav>
  </header>
  <main class="app-main">
    <RouterView />
  </main>
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
}

.brand-logo {
  width: 2rem;
  height: 2rem;
}

.nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.nav a {
  color: inherit;
  text-decoration: none;
}

.nav a:hover {
  text-decoration: underline;
}

.account {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.logout {
  cursor: pointer;
}
</style>
