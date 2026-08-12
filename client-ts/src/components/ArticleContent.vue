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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Viewer from 'viewerjs'
import { useMediaCards } from '@/composables/useMediaCards'
import { normalizeContent, renderBody } from '@/utils/articleContent'
import type { ArticleContent } from '@/types/article'

const props = defineProps<{
  content: ArticleContent | string
}>()

const root = ref<HTMLElement | null>(null)
const mediaCards = useMediaCards()
let viewer: Viewer | null = null
let singleViewer: Viewer | null = null

const html = computed<string>(() => {
  const { body, mediaRefs } = normalizeContent(props.content)
  return renderBody(body, mediaRefs)
})

watch(
  html,
  async () => {
    await nextTick()
    if (root.value) {
      await mediaCards.fill(root.value)
      initViewer()
    }
  },
  { immediate: true },
)

/** 重建图片全屏预览实例（内容更新后重新扫描图片） */
function initViewer(): void {
  viewer?.destroy()
  viewer = null
  if (root.value) {
    viewer = new Viewer(root.value, {
      zIndex: 2000,
      title: false,
      filter: (img) => !img.classList.contains('media-card-cover-img'),
      toolbar: {
        zoomIn: true,
        zoomOut: true,
        oneToOne: true,
        reset: true,
        prev: false,
        play: false,
        next: false,
        rotateLeft: true,
        rotateRight: true,
        flipHorizontal: true,
        flipVertical: true,
      },
    })
  }
}

/** 音频封面单独预览（不进入文章图片列表；单图 viewer 不能带 filter，否则封面自身会被排除） */
function openSingleViewer(img: HTMLImageElement): void {
  singleViewer?.destroy()
  singleViewer = new Viewer(img, {
    zIndex: 2001,
    title: false,
    toolbar: {
      zoomIn: true,
      zoomOut: true,
      oneToOne: true,
      reset: true,
      rotateLeft: true,
      rotateRight: true,
      flipHorizontal: true,
      flipVertical: true,
    },
  })
  // viewerjs 无实例 on() 方法，事件以 DOM 事件形式派发在 element 上
  img.addEventListener(
    'hidden',
    () => {
      singleViewer?.destroy()
      singleViewer = null
    },
    { once: true },
  )
  singleViewer.show()
}

function onRootClick(event: MouseEvent): void {
  const target = event.target as HTMLElement
  if (target instanceof HTMLImageElement && target.classList.contains('media-card-cover-img')) {
    openSingleViewer(target)
    return
  }
  mediaCards.handleClick(event)
}

onBeforeUnmount(() => {
  viewer?.destroy()
  viewer = null
  singleViewer?.destroy()
  singleViewer = null
  mediaCards.dispose()
})
</script>

<template>
  <div ref="root" class="article-content" v-html="html" @click="onRootClick"></div>
</template>

<style scoped>
.article-content {
  line-height: 1.8;
  word-break: break-word;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) {
  margin: 1.5em 0 0.5em;
  line-height: 1.3;
  scroll-margin-top: 4.5rem;
}

.article-content :deep(p) {
  margin: 0.5em 0;
}

.article-content :deep(img) {
  max-width: 100%;
  max-height: calc(100cqw * 9 / 16);
  border-radius: 4px;
  cursor: zoom-in;
}

.article-content :deep(a) {
  color: var(--primary);
}

.article-content :deep(code) {
  padding: 0.15em 0.4em;
  background: var(--code-bg);
  border-radius: 3px;
  font-size: 0.9em;
}

.article-content :deep(pre) {
  padding: 1rem;
  overflow-x: auto;
  background: var(--code-block-bg);
  color: var(--code-block-text);
  border-radius: 6px;
}

.article-content :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}

.article-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.25em 1em;
  border-left: 4px solid var(--border-strong);
  color: var(--text-muted);
}

.article-content :deep(table) {
  border-collapse: collapse;
}

.article-content :deep(th),
.article-content :deep(td) {
  padding: 0.4em 0.8em;
  border: 1px solid var(--border-strong);
}

.article-content :deep(.media-card) {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin: 0.75em 0;
  padding: 0.75rem 1rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
}

.article-content :deep(.media-card-cover) {
  flex-shrink: 0;
  width: 4.5rem;
  height: 4.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-muted);
  border-radius: 8px;
  overflow: hidden;
}

.article-content :deep(.media-card-cover-img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}

.article-content :deep(.media-card-type) {
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}

.article-content :deep(.media-card-body) {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.article-content :deep(.media-card-name) {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.article-content :deep(.media-card video) {
  width: 100%;
  max-height: 22rem;
  border-radius: 8px;
  background: #000;
}

.article-content :deep(.media-video) {
  margin: 0.75em 0;
}

.article-content :deep(.media-video-player) {
  width: 100%;
  aspect-ratio: 16 / 9;
  max-height: 70svh;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.article-content :deep(.media-video-player video) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.article-content :deep(.media-video-title) {
  display: block;
  margin-top: 0.4rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-strong);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.article-content :deep(.media-video-unsupported) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 0.5rem;
  padding: 0.6rem 0.9rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  background: var(--danger-soft);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.article-content :deep(.media-card-download) {
  align-self: flex-start;
  padding: 0.3rem 0.9rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--on-primary);
  background: var(--primary);
  border-radius: 6px;
  text-decoration: none;
  transition: background 0.2s ease;
}

.article-content :deep(.media-card-download:hover) {
  background: var(--primary-hover);
}

.article-content :deep(.media-card-size) {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.article-content :deep(.media-icon-btn) {
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 6px;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.article-content :deep(.media-icon-btn:hover) {
  color: var(--primary-hover);
  background: var(--primary-soft-border);
}

.article-content :deep(.media-icon-btn svg) {
  width: 1.1rem;
  height: 1.1rem;
  fill: currentColor;
}

.article-content :deep(.audio-player) {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.article-content :deep(.audio-toggle) {
  flex-shrink: 0;
  width: 2.2rem;
  height: 2.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: none;
  color: var(--primary);
  cursor: pointer;
  transition: color 0.2s ease;
}

.article-content :deep(.audio-toggle:hover) {
  color: var(--primary-hover);
}

.article-content :deep(.audio-toggle svg) {
  width: 1.1rem;
  height: 1.1rem;
  fill: currentColor;
}

.article-content :deep(.audio-toggle .audio-icon-pause) {
  display: none;
}

.article-content :deep(.audio-toggle.is-playing .audio-icon-play) {
  display: none;
}

.article-content :deep(.audio-toggle.is-playing .audio-icon-pause) {
  display: block;
}

.article-content :deep(.audio-progress) {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 0.6rem 0;
  cursor: pointer;
}

.article-content :deep(.audio-progress-track) {
  flex: 1;
  height: 4px;
  background: var(--bg-muted);
  border-radius: 2px;
  overflow: hidden;
}

.article-content :deep(.audio-progress-fill) {
  height: 100%;
  width: 0;
  background: var(--primary);
  border-radius: 2px;
}

.article-content :deep(.audio-time) {
  flex-shrink: 0;
  font-size: 0.75rem;
  color: var(--text-faint);
  white-space: nowrap;
}

.article-content :deep(.audio-volume) {
  position: relative;
  flex-shrink: 0;
}

.article-content :deep(.audio-volume-pop) {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  opacity: 0;
  visibility: hidden;
  padding: 0.5rem 0.6rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
  transition:
    opacity 0.15s ease,
    visibility 0.15s;
}

.article-content :deep(.audio-volume:hover .audio-volume-pop),
.article-content :deep(.audio-volume-pop:hover) {
  opacity: 1;
  visibility: visible;
}

.article-content :deep(.audio-volume-toggle) {
  flex-shrink: 0;
  width: 1.6rem;
  height: 1.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
  transition: color 0.2s ease;
}

.article-content :deep(.audio-volume-toggle:hover) {
  color: var(--primary);
}

.article-content :deep(.audio-volume-toggle svg) {
  width: 1.05rem;
  height: 1.05rem;
  fill: currentColor;
}

.article-content :deep(.audio-volume-toggle .audio-icon-vol-muted) {
  display: none;
}

.article-content :deep(.audio-volume-toggle.is-muted .audio-icon-vol) {
  display: none;
}

.article-content :deep(.audio-volume-toggle.is-muted .audio-icon-vol-muted) {
  display: block;
}

.article-content :deep(.audio-volume-bar) {
  position: relative;
  width: 4px;
  height: 4.5rem;
  background: var(--bg-muted);
  border-radius: 2px;
  overflow: hidden;
  cursor: pointer;
}

.article-content :deep(.audio-volume-fill) {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: var(--primary);
  border-radius: 2px;
}
</style>
