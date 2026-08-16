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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteArticle, getUserArticles } from '@/api/article'
import { avatarErrorFallback, avatarUrl, getUserProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'
import ArticleCard from '@/components/ArticleCard.vue'
import EditProfileDialog from '@/components/EditProfileDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import SearchBox from '@/components/SearchBox.vue'
import { toast } from '@/utils/toast'
import type { ArticleSummary } from '@/types/article'
import type { CurrentUser } from '@/types/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 当前用户 id（computed：SPA 内 /user/1 → /user/2 跳转时随路由更新） */
const userId = computed(() => Number(route.params.userId) || 0)
/** 与当前登录账号一致时展示草稿并允许删除/编辑资料 */
const isOwn = computed(() => userStore.userId === userId.value && userStore.isLoggedIn)

const profile = ref<CurrentUser | null>(null)
const published = ref<ArticleSummary[]>([])
const drafts = ref<ArticleSummary[]>([])
const loading = ref(true)
const deletingId = ref<number | null>(null)
const searchQuery = ref('')

const editOpen = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 加载用户文章。keyword 非空时请求后端过滤（GET /users/{id}/articles?keyword=）；
 * silent=true 用于搜索防抖刷新（不显示整页 loading）
 */
async function load(keyword?: string, silent = false): Promise<void> {
  if (!silent) loading.value = true
  try {
    // 页面级加载时强制从后端校验 userId（本地持久化的可能已过期）；
    // 搜索防抖（silent）不再重复拉取 /users/me
    await userStore.ensureUserId(!silent)
    const [p, articles] = await Promise.all([getUserProfile(userId.value), getUserArticles(userId.value, keyword)])
    profile.value = p.data
    const list = articles.data
    published.value = list.filter((a) => a.status === 'published')
    drafts.value = list.filter((a) => a.status === 'draft')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    if (!silent) loading.value = false
  }
}

/** 搜索输入防抖 300ms 后请求后端过滤 */
watch(searchQuery, (q) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void load(q.trim() || undefined, true)
  }, 300)
})

/** SPA 内用户主页间跳转（/user/1 → /user/2）：重置状态并重新加载 */
watch(() => route.params.userId, () => {
  searchQuery.value = ''
  void load()
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

async function removeArticle(article: ArticleSummary): Promise<void> {
  const label = article.title || `文章 #${article.id}`
  if (!window.confirm(`确定删除「${label}」吗？删除后不可恢复。`)) return
  deletingId.value = article.id
  try {
    await deleteArticle(article.id)
    published.value = published.value.filter((a) => a.id !== article.id)
    drafts.value = drafts.value.filter((a) => a.id !== article.id)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    deletingId.value = null
  }
}

function goEdit(article: ArticleSummary): void {
  router.push(`/editor/${article.id}`)
}

/** 保存资料成功后重新拉取用户信息 */
function onProfileSaved(): void {
  editOpen.value = false
  void load()
}

onMounted(load)
</script>

<template>
  <div class="user-page">
    <div
      class="user-bg"
      :style="{ '--user-bg-image': `url(${avatarUrl(userId)}?v=${userStore.avatarVersion})` }"
      aria-hidden="true"
    ></div>
    <header class="profile">
      <div class="avatar">
        <img
          :src="`${avatarUrl(userId)}?v=${userStore.avatarVersion}`"
          alt="用户头像"
          @error="avatarErrorFallback"
        />
      </div>
      <div class="profile-body">
        <h1 class="name">{{ profile?.username || profile?.account || `用户 #${userId}` }}</h1>
        <p v-if="profile" class="sub">@{{ profile.account }}</p>
        <p v-else class="sub">用户 #{{ userId }}</p>
      </div>
      <div v-if="isOwn" class="profile-actions">
        <button class="btn-primary" type="button" @click="editOpen = true">编辑资料</button>
      </div>
    </header>

    <p v-if="loading" class="state">加载中…</p>
    <template v-else>
      <SearchBox
        v-if="published.length || drafts.length"
        v-model="searchQuery"
        placeholder="搜索文章标题或摘要…"
      />
      <section class="section">
        <h2 class="section-title">已发布（{{ published.length }}）</h2>
        <div v-if="published.length" class="grid">
          <div v-for="article in published" :key="article.id" class="card-item">
            <ArticleCard :article="article" compact />
            <div v-if="isOwn" class="card-actions">
              <button class="btn-action" type="button" title="编辑" aria-label="编辑" @click="goEdit(article)">
                <AppIcon name="write" />
              </button>
              <button class="btn-action danger" type="button" title="删除" aria-label="删除" :disabled="deletingId === article.id" @click="removeArticle(article)">
                <AppIcon name="trash" />
              </button>
            </div>
          </div>
        </div>
        <p v-else class="state hint">{{ searchQuery ? '没有匹配的文章。' : '还没有已发布的文章。' }}</p>
      </section>

      <section v-if="isOwn" class="section">
        <h2 class="section-title">草稿（{{ drafts.length }}）</h2>
        <div v-if="drafts.length" class="grid">
          <div v-for="article in drafts" :key="article.id" class="card-item">
            <ArticleCard :article="article" :to="`/editor/${article.id}`" compact />
            <div class="card-actions">
              <button class="btn-action" type="button" title="继续编辑" aria-label="继续编辑" @click="goEdit(article)">
                <AppIcon name="write" />
              </button>
              <button class="btn-action danger" type="button" title="删除" aria-label="删除" :disabled="deletingId === article.id" @click="removeArticle(article)">
                <AppIcon name="trash" />
              </button>
            </div>
          </div>
        </div>
        <p v-else class="state hint">{{ searchQuery ? '没有匹配的文章。' : '暂无草稿。' }}</p>
      </section>

      <p v-if="!isOwn && !published.length && !loading" class="state hint">该用户还没有发布文章。</p>
    </template>
  </div>

  <EditProfileDialog
    v-if="editOpen"
    :user-id="userId"
    :initial-name="profile?.username || ''"
    @close="editOpen = false"
    @saved="onProfileSaved"
  />
</template>

<style scoped>
.user-page {
  position: relative;
  z-index: 1;
  max-width: 64rem;
  margin: 0 auto;
  padding: 2rem 1.5rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.user-bg {
  position: fixed;
  inset: 0;
  /* 负 z-index：位于页面内容之下、页面背景之上，不遮挡任何 UI */
  z-index: -1;
  background-image: linear-gradient(rgba(var(--bg-page-rgb), 0.88), rgba(var(--bg-page-rgb), 0.88)),
    var(--user-bg-image, none);
  background-size: cover;
  background-position: center;
  filter: blur(100px) saturate(140%);
  transform: scale(1.2);
  pointer-events: none;
}

.profile {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
}

.avatar {
  flex-shrink: 0;
  width: 5rem;
  height: 5rem;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.profile-body {
  flex: 1;
  min-width: 0;
}

.name {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 600;
}

.sub {
  margin: 0.25rem 0 0;
  font-size: 0.85rem;
  color: var(--text-faint);
}

.section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.section-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-strong);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.card-item {
  position: relative;
}

.card-actions {
  position: absolute;
  right: 0.6rem;
  bottom: 0.6rem;
  display: flex;
  gap: 0.35rem;
  padding: 0;
  background: none;
  border: none;
  box-shadow: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.card-item:hover .card-actions,
.card-actions:focus-within {
  opacity: 1;
}

.btn-action {
  width: 1.9rem;
  height: 1.9rem;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: var(--primary);
  /* 半透明圆底：无边框，图片上也可读，且不形成遮盖块 */
  background: color-mix(in srgb, var(--bg-card) 72%, transparent);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.btn-action:hover:not(:disabled) {
  background: var(--primary-soft);
}

.btn-action svg {
  width: 1rem;
  height: 1rem;
  fill: currentColor;
}

.btn-action.danger {
  color: var(--danger);
}

.btn-action.danger:hover:not(:disabled) {
  background: var(--danger-soft);
}

.btn-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  flex-shrink: 0;
  padding: 0.55rem 1.5rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--on-primary);
  background: var(--primary);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.2s ease;
}

.btn-primary:hover {
  background: var(--primary-hover);
}

.profile-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.state {
  color: var(--text-faint);
  text-align: center;
  padding: 3rem 0;
}

.error {
  color: var(--danger);
}

.hint {
  padding: 1.5rem 0;
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  /* 移动端无 hover：按钮常显，但仍悬浮在卡片内部（保持 absolute 定位） */
  .card-actions {
    opacity: 1;
  }

  .btn-action {
    width: 2.2rem;
    height: 2.2rem;
  }

  .profile {
    flex-wrap: wrap;
  }
}
</style>
