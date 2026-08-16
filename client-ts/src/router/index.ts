/*
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
 */

import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '@/api/http'
import { IS_ADMIN_KEY } from '@/stores/user'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import ArticleView from '@/views/ArticleView.vue'
import EditorView from '@/views/EditorView.vue'
import AdminView from '@/views/AdminView.vue'
import UserView from '@/views/UserView.vue'
import SearchView from '@/views/SearchView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    /** 需管理员权限（与后端 ADMIN authority 对应；非法访问重定向首页） */
    requiresAdmin?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/search', name: 'search', component: SearchView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/article/:articleId', name: 'article', component: ArticleView },
    { path: '/user/:userId', name: 'user', component: UserView },
    { path: '/editor', name: 'editor-new', component: EditorView, meta: { requiresAuth: true } },
    { path: '/editor/:articleId', name: 'editor-edit', component: EditorView, meta: { requiresAuth: true } },
    { path: '/admin', name: 'admin', component: AdminView, meta: { requiresAuth: true, requiresAdmin: true } },
    // 404 兜底：未匹配路径重定向首页（避免空白页）
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem(TOKEN_KEY))
  if (to.meta.requiresAuth && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && loggedIn) {
    return { path: '/' }
  }
  // 管理员前置守卫（与后端 ADMIN authority 对齐；本地 isAdmin 标记缺失时重定向首页，
  // 页面级 403 兜底仍保留）
  if (to.meta.requiresAdmin && localStorage.getItem(IS_ADMIN_KEY) !== '1') {
    return { path: '/' }
  }
})

export default router
