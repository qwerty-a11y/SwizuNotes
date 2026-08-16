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
import { useRoute } from 'vue-router'
import { getArticle } from '@/api/article'
import { mediaUrl } from '@/api/media'
import { avatarErrorFallback, avatarUrl, getUserProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'
import AppIcon from '@/components/AppIcon.vue'
import ArticleContent from '@/components/ArticleContent.vue'
import { extractHeadings, normalizeContent } from '@/utils/articleContent'
import { toast } from '@/utils/toast'
import type { Article } from '@/types/article'
import type { CurrentUser } from '@/types/user'

const route = useRoute()
const userStore = useUserStore()

const article = ref<Article | null>(null)
/** 作者信息（独立作者行展示，加载失败不影响文章正文） */
const author = ref<CurrentUser | null>(null)

/** 移动端悬浮目录：展开状态 + 面板/按钮引用（用于点击外部关闭） */
const tocOpen = ref(false)
const tocPanel = ref<HTMLElement | null>(null)
const tocFab = ref<HTMLElement | null>(null)

const toc = computed(() => {
  if (!article.value) return []
  return extractHeadings(normalizeContent(article.value.content).body)
})

function toggleToc(): void {
  tocOpen.value = !tocOpen.value
}

function jumpTo(id: string): void {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
  tocOpen.value = false
}

/** 点击悬浮窗/按钮以外区域时收起 */
function onDocClick(e: MouseEvent): void {
  if (!tocOpen.value) return
  const target = e.target as Node | null
  if (!target) return
  if (tocPanel.value?.contains(target) || tocFab.value?.contains(target)) return
  tocOpen.value = false
}

async function load(): Promise<void> {
  // 重置状态（SPA 内文章间跳转时复用组件）
  article.value = null
  author.value = null
  try {
    const { data } = await getArticle(Number(route.params.articleId))
    article.value = data
    try {
      author.value = (await getUserProfile(data.authorId)).data
    } catch {
      // 作者信息获取失败不影响文章展示（作者行降级为仅显示 id）
    }
  } catch (e) {
    toast.error((e as Error).message)
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  void load()
})

/** SPA 内文章间跳转（/article/1 → /article/2）：重新加载（组件复用不会重新挂载） */
watch(() => route.params.articleId, () => void load())

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})
</script>

<template>
  <div class="page" :class="{ 'has-toc': toc.length }" v-if="article">
    <div
      v-if="article.cover"
      class="page-bg"
      :style="{ '--page-bg-image': `url(${mediaUrl(article.cover)})` }"
      aria-hidden="true"
    ></div>
    <article class="article-card">
      <img v-if="article.cover" class="cover" :src="mediaUrl(article.cover)" alt="封面图" />
      <div v-else class="cover cover-placeholder">SwizuNotes</div>
      <div class="card-body">
        <h1>{{ article.title || `文章 #${article.id}` }}</h1>
        <!-- 作者行：头像 + 昵称 + 账号，点击进入个人主页 -->
        <RouterLink class="author-card" :to="`/user/${article.authorId}`">
          <span class="author-avatar">
            <img
              :src="`${avatarUrl(article.authorId)}?v=${userStore.avatarVersion}`"
              alt="作者头像"
              @error="avatarErrorFallback"
            />
          </span>
          <span class="author-info">
            <span class="author-name">{{ author?.username || author?.account || `用户 #${article.authorId}` }}</span>
            <span class="author-account">@{{ author?.account || `#${article.authorId}` }}</span>
          </span>
        </RouterLink>
        <p class="meta">
          <template v-if="article.publishTime">发布时间：{{ new Date(article.publishTime).toLocaleString() }}</template>
          <template v-if="article.modifyTime"> · 修改时间：{{ new Date(article.modifyTime).toLocaleString() }}</template>
        </p>
        <p v-if="article.summary" class="summary">{{ article.summary }}</p>
        <ArticleContent :content="article.content" />
      </div>
    </article>
    <aside class="toc" v-if="toc.length" :class="{ open: tocOpen }" ref="tocPanel">
      <h2 class="toc-title">目录</h2>
      <ul class="toc-list">
        <li v-for="item in toc" :key="item.id" :class="`level-${item.level}`">
          <a :href="`#${item.id}`" @click.prevent="jumpTo(item.id)">{{ item.text }}</a>
        </li>
      </ul>
    </aside>
    <!-- 移动端悬浮目录开关按钮（桌面端隐藏） -->
    <button
      v-if="toc.length"
      ref="tocFab"
      class="toc-fab"
      :aria-expanded="tocOpen"
      aria-label="目录"
      @click="toggleToc"
    >
      <AppIcon :name="tocOpen ? 'close' : 'list'" />
    </button>
  </div>
</template>

<style scoped>
.page {
  position: relative;
  max-width: 78rem;
  min-width: 0;
  margin: 0 auto;
  padding: 2rem 1.5rem 3rem;
  /* grid：1fr 轨道的宽度分配是确定性的，卡片宽度只与容器宽度有关、与内容完全无关；
     minmax(0, 1fr) 防止内容把轨道撑宽 */
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
  gap: 1.5rem;
}

/* 有目录时第二列固定 15rem */
.page.has-toc {
  grid-template-columns: minmax(0, 1fr) 15rem;
}

.page-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background-image: linear-gradient(rgba(var(--bg-page-rgb), 0.7), rgba(var(--bg-page-rgb), 0.7)),
    var(--page-bg-image, none);
  background-size: cover;
  background-position: center;
  filter: blur(60px) saturate(140%);
  transform: scale(1.2);
  pointer-events: none;
}

.article-card,
.toc {
  position: relative;
  z-index: 1;
}

.article-card {
  /* 宽度由 grid 轨道（minmax(0,1fr)）决定，与内容无关；
     min-height 兜底空白文章不塌成扁条 */
  min-width: 0;
  min-height: 18rem;
  background: color-mix(in srgb, var(--bg-card) 75%, transparent);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.cover {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 1;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  /* 与 .cover 一致：无封面文章也保持 3:1 封面区，避免塌成细条 */
  aspect-ratio: 3 / 1;
  background: var(--bg-muted);
  color: var(--text-faint);
  font-size: 0.9rem;
  letter-spacing: 0.05em;
}

.card-body {
  container-type: inline-size;
  min-width: 0;
  padding: 1.5rem 2rem 2.5rem;
}

.card-body h1 {
  margin: 0 0 0.5rem;
  font-size: 2rem;
  line-height: 1.3;
}

.meta {
  color: var(--text-muted);
  font-size: 0.9rem;
}

/* 独立作者行：头像 + 昵称 + 账号，整行可点击进入个人主页 */
.author-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: fit-content;
  max-width: 100%;
  margin: 0.75rem 0 0.75rem;
  border-radius: 10px;
  text-decoration: none;
  color: var(--text);
  transition: background-color 0.2s ease;
}

.author-card:hover {
  background: color-mix(in srgb, var(--bg-muted) 65%, transparent);
}

.author-avatar {
  flex-shrink: 0;
  width: 3.25rem;
  height: 3.25rem;
  border-radius: 50%;
  overflow: hidden;
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}

.author-avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.author-name {
  font-weight: 600;
  line-height: 1.3;
}

.author-account {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.summary {
  color: var(--text-muted);
  font-style: italic;
}

.toc {
  position: sticky;
  top: 4.5rem;
  max-height: calc(100svh - 6rem);
  overflow-y: auto;
  background: color-mix(in srgb, var(--bg-card) 75%, transparent);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: var(--shadow-card);
}

.toc-title {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  font-weight: 600;
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.toc-list a {
  display: block;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.4;
  text-decoration: none;
  transition: color 0.2s ease;
}

.toc-list a:hover {
  color: var(--primary);
}

.level-2 {
  padding-left: 1rem;
}

.level-3 {
  padding-left: 2rem;
}

.level-4,
.level-5,
.level-6 {
  padding-left: 3rem;
}

.error {
  color: var(--danger);
  text-align: center;
}

.toc-fab {
  display: none;
}

@media (max-width: 1024px) {
  .page,
  .page.has-toc {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 移动端：目录改为右侧可折叠悬浮窗（默认完全隐藏，右侧圆形按钮切换展开/收起），
     不再占文章上方的文档流位置；展开动画从按钮方向"长出"（transform-origin 右缘中心），
     隐藏态 opacity 0 + visibility hidden（彻底不可见、不拦截点击）；
     z-index 高于导航栏(100) */
  .toc {
    position: fixed;
    top: 6rem;
    right: 4.25rem;
    width: min(16rem, calc(100vw - 5rem));
    max-height: 60svh;
    z-index: 110;
    transform-origin: right center;
    transform: translateX(0.75rem) scale(0.85);
    opacity: 0;
    visibility: hidden;
    transition:
      transform 0.3s ease,
      opacity 0.3s ease,
      visibility 0.3s;
  }

  .toc.open {
    transform: translateX(0) scale(1);
    opacity: 1;
    visibility: visible;
  }

  .toc-fab {
    display: flex;
    position: fixed;
    top: 6rem;
    right: 0.75rem;
    z-index: 120;
    width: 2.75rem;
    height: 2.75rem;
    border: none;
    border-radius: 50%;
    background: var(--primary);
    color: var(--on-primary);
    box-shadow: var(--shadow-card);
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background-color 0.2s ease;
  }

  .toc-fab svg {
    width: 1.4rem;
    height: 1.4rem;
  }
}
</style>
