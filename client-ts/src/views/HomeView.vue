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
import ArticleCard from '@/components/ArticleCard.vue'
import type { ArticleSummary } from '@/types/article'
import heroImg from '@/assets/banner.png'

const articles = ref<ArticleSummary[]>([])
const loading = ref(true)
const error = ref('')
const expanded = ref(false)
let lastTouchY = 0

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
    error.value = (e as Error).message
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
    <section class="banner" :class="{ expanded }">
      <div class="banner-text">
        <h1>SwizuNotes</h1>
        <p>记录、思考与分享</p>
      </div>
    </section>

    <section class="articles">
      <p v-if="loading" class="state">加载中…</p>
      <p v-else-if="error" class="state error">{{ error }}</p>
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
  justify-content: space-between;
  gap: 2rem;
  padding: 2.5rem 3rem;
  margin: 0;
  border-radius: 0;
  color: var(--on-primary);
  background-image: url('@/assets/banner.png');
  background-size: cover;
  background-position: center 30%;
  overflow: hidden;
  transition: height 0.6s cubic-bezier(0.4, 0, 0.2, 1);
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
  transition: opacity 0.6s ease;
}

.banner::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--bg-overlay);
  opacity: 0;
  pointer-events: none;
  z-index: 2;
  transition: opacity 0.6s ease;
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
  opacity: 0;
  transition:
    opacity 0.6s ease,
    transform 0.6s cubic-bezier(0.4, 0, 0.2, 1),
    text-align 0.6s ease;
}

.banner.expanded .banner-text {
  opacity: 1;
  transform: translateY(8vh) scale(1.3);
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
