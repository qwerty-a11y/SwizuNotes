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
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getArticles } from '@/api/article'
import AppIcon from '@/components/AppIcon.vue'
import ArticleCard from '@/components/ArticleCard.vue'
import { toast } from '@/utils/toast'
import type { ArticleSummary } from '@/types/article'

const articles = ref<ArticleSummary[]>([])
const loading = ref(true)
const expanded = ref(false)
const bannerEl = ref<HTMLElement | null>(null)
let lastTouchY = 0

/** 全屏查看横幅时下载当前主题的横幅图（解析 --banner-bg-image 变量的 URL） */
function downloadBanner(): void {
  const banner = bannerEl.value
  if (!banner) return
  const img = getComputedStyle(banner).getPropertyValue('--banner-bg-image').trim()
  const m = /^url\(["']?(.+?)["']?\)$/.exec(img)
  if (!m) return
  const a = document.createElement('a')
  a.href = m[1]
  a.download = m[1].split('/').pop() || 'banner.png'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

function collapse(): void {
  expanded.value = false
}

function onWheel(e: WheelEvent): void {
  if (e.deltaY < 0) {
    if (window.scrollY === 0) expanded.value = true
  } else {
    collapse()
  }
}

function onTouchStart(e: TouchEvent): void {
  lastTouchY = e.touches[0].clientY
}

function onTouchMove(e: TouchEvent): void {
  const y = e.touches[0].clientY
  const pulling = y > lastTouchY
  lastTouchY = y
  if (pulling && window.scrollY === 0) {
    expanded.value = true
  } else if (!pulling) {
    collapse()
  }
}

function onScroll(): void {
  if (window.scrollY > 0) collapse()
}

onMounted(async () => {
  window.addEventListener('wheel', onWheel, { passive: true })
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: true })
  window.addEventListener('scroll', onScroll, { passive: true })
  try {
    articles.value = (await getArticles()).data
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('wheel', onWheel)
  window.removeEventListener('touchstart', onTouchStart)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <div class="home">
    <section ref="bannerEl" class="banner" :class="{ expanded }">
      <!-- 模糊背景层（移动端展开时铺满，完整图未覆盖的区域） -->
      <div class="banner-bg" aria-hidden="true"></div>
      <!-- 完整图背景层（移动端展开时 contain 完整展示） -->
      <div class="banner-image" aria-hidden="true"></div>
      <!-- 全屏查看横幅图时右下角下载按钮 -->
      <button v-if="expanded" class="banner-download" title="下载横幅图" aria-label="下载横幅图" @click="downloadBanner">
        <AppIcon name="download" />
      </button>
      <div class="banner-text">
        <h1>SwizuNotes</h1>
        <p>记录、思考与分享</p>
      </div>
    </section>

    <section class="articles">
      <p v-if="loading" class="state">加载中…</p>
      <div v-else-if="articles.length" class="grid">
        <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
      </div>
      <p v-else class="state hint">还没有已发布的文章。</p>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding: 0;
  margin-top: -57px;
}

.banner {
  position: relative;
  height: 42svh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2rem;
  padding: 2.5rem 3rem;
  margin: 0;
  border-radius: 0;
  color: var(--on-primary);
  /* 桌面端：cover 填满全屏，完整展示图片 */
  background-image: var(--banner-bg-image);
  background-size: cover;
  background-position: center 30%;
  overflow: hidden;
  /* 无过冲缓动：展开/收回末端柔和；overflow-anchor 防止高度变化时滚动位置被浏览器调整 */
  transition: height 0.6s cubic-bezier(0.22, 0.61, 0.36, 1);
  overflow-anchor: none;
}

.banner::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 50%;
  background: linear-gradient(to bottom, rgba(var(--bg-page-rgb), 0), var(--bg-page) 90%);
  pointer-events: none;
  z-index: 3;
  transition: opacity 0.6s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.banner::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--bg-overlay);
  opacity: 0;
  pointer-events: none;
  z-index: 2;
  transition: opacity 0.6s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.banner.expanded {
  height: 100svh;
}

.banner.expanded::after {
  opacity: 0;
}

.banner.expanded::before {
  opacity: 1;
}

.banner h1 {
  margin: 0;
  font-size: 2.25rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--on-primary);
}

.banner p {
  margin: 0.5rem 0 0;
  font-size: 1rem;
  opacity: 0.9;
}

.banner-text {
  position: relative;
  z-index: 4;
  text-align: center;
  max-width: 90vw;
  opacity: 0;
  transition:
    opacity 0.6s ease,
    transform 0.6s cubic-bezier(0.22, 0.61, 0.36, 1);
}

/* 全屏查看横幅图时右下角的下载按钮（亚克力风格：半透明卡片 + 毛玻璃，与全站一致） */
.banner-download {
  position: absolute;
  right: 1.5rem;
  bottom: 1.5rem;
  z-index: 5;
  width: 2.75rem;
  height: 2.75rem;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: color-mix(in srgb, var(--bg-card) 60%, transparent);
  backdrop-filter: blur(12px);
  color: var(--text);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.banner-download:hover {
  background: color-mix(in srgb, var(--bg-card) 85%, transparent);
  color: var(--primary);
}

.banner-download svg {
  width: 1.3rem;
  height: 1.3rem;
  fill: currentColor;
}

.banner.expanded .banner-text {
  opacity: 1;
  transform: translateY(8vh) scale(1.3);
}

/* 背景层：桌面端隐藏（banner 直接用 background-image cover 完整展示） */
.banner-bg,
.banner-image {
  display: none;
}

/* 移动端：图片层固定为默认 banner 高度（42svh）的 cover 缩放，
   展开/收回时高度变化不影响图片缩放（层高度不变，仅被容器裁切），
   展开时模糊背景淡入 + 图片边缘 mask 羽化过渡 */
@media (max-width: 768px) {
  .banner {
    background-image: none;
  }

  .banner-bg {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 0;
    background-image: var(--banner-bg-image);
    background-size: cover;
    background-position: center;
    filter: blur(28px) saturate(1.2);
    transform: scale(1.12);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.6s cubic-bezier(0.22, 0.61, 0.36, 1);
  }

  .banner-image {
    display: block;
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: 42svh;
    transform: translateY(-50%);
    z-index: 1;
    background-image: var(--banner-bg-image);
    background-size: cover;
    background-position: center 30%;
    background-repeat: no-repeat;
  }

  .banner.expanded .banner-bg {
    opacity: 1;
  }

  /* 展开时图片边缘（相对图片自身 42svh 高度）羽化，平滑过渡到模糊背景 */
  .banner.expanded .banner-image {
    -webkit-mask-image: linear-gradient(to bottom, transparent, #000 10%, #000 90%, transparent);
    mask-image: linear-gradient(to bottom, transparent, #000 10%, #000 90%, transparent);
  }
}

.articles {
  max-width: 64rem;
  margin: 0 auto;
  padding: 2.5rem 1.5rem 3rem;
  padding-left: 4.5rem;
}

@media (max-width: 1024px) {
  .articles {
    padding: 2rem 1rem 2.5rem;
  }

  .banner {
    padding: 1.75rem 1.5rem;
  }
}

.grid {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.state {
  color: var(--text-faint);
  text-align: center;
  padding: 3rem 0;
}

.error {
  color: var(--danger);
}
</style>
