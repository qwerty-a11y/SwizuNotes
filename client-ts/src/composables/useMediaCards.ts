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

import Artplayer from 'artplayer'
import { getMediaInfo, mediaUrl } from '@/api/media'
import type { MediaCategory } from '@/types/media'

interface MediaMeta {
  name?: string
  imageId?: string
  duration?: number
}

interface MediaInfo {
  id: string
  articleId: number
  type: MediaCategory
  mimeType: string
  metadata: string
  size?: number | null
}

interface AudioPlayer {
  audio: HTMLAudioElement
  card: HTMLElement
  toggleBtn: HTMLButtonElement
  fill: HTMLElement
  timeEl: HTMLElement
  seekBar: HTMLElement
  volumeToggle: HTMLButtonElement
  volumeBar: HTMLElement
  volumeFill: HTMLElement
  dragging: boolean
  volumeDragging: boolean
}

export interface MediaCards {
  /** 填充容器内所有媒体卡片（并行 info + 初始化播放器） */
  fill(container: HTMLElement): Promise<void>
  /** 容器点击委托（播放/静音按钮、进度/音量条点击） */
  handleClick(event: MouseEvent): void
  /** 释放所有播放器实例 */
  dispose(): void
}

export function useMediaCards(): MediaCards {
  const players = new Map<string, AudioPlayer>()
  const videoPlayers = new Map<string, Artplayer>()

  async function fill(container: HTMLElement): Promise<void> {
    const cards = Array.from(
      container.querySelectorAll<HTMLElement>('.media-card[data-media-id], .media-video[data-media-id]'),
    )
    await mapLimit(cards, 4, processCard)
  }

  async function mapLimit<T>(items: T[], limit: number, fn: (item: T) => Promise<void>): Promise<void> {
    let index = 0
    const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
      while (index < items.length) {
        const item = items[index]
        index += 1
        await fn(item)
      }
    })
    await Promise.all(workers)
  }

  async function processCard(card: HTMLElement): Promise<void> {
    const mediaId = card.dataset.mediaId
    const url = card.dataset.url
    if (!mediaId || !url) return

    if (card.classList.contains('media-audio')) {
      bindAudioPlayer(card, mediaId, url)
    }
    if (card.classList.contains('media-video')) {
      bindVideoPlayer(card, mediaId, url)
    }

    if (card.dataset.mediaReady) return

    let info: MediaInfo | null = null
    try {
      info = (await getMediaInfo(mediaId)).data
    } catch {
      // 保持占位状态（名称已回退为 alias）
    }
    if (!info) return

    const meta = safeParseMeta(info.metadata)
    const name = meta?.name || `${info.type} #${mediaId.slice(0, 8)}`
    const nameEl = card.querySelector<HTMLElement>('.media-card-name, .media-video-title')
    if (nameEl) {
      nameEl.textContent = name
      nameEl.title = name
    }
    const sizeEl = card.querySelector<HTMLElement>('.media-card-size')
    if (sizeEl && typeof info.size === 'number' && info.size >= 0) {
      sizeEl.textContent = formatSize(info.size)
    }
    if (info.type === 'audio' && meta?.imageId) {
      const cover = card.querySelector<HTMLElement>('.media-card-cover')
      if (cover && !cover.querySelector('.media-card-cover-img')) {
        cover.innerHTML = ''
        const img = document.createElement('img')
        img.src = mediaUrl(meta.imageId)
        img.alt = name
        img.className = 'media-card-cover-img'
        cover.appendChild(img)
      }
    }
    card.dataset.mediaReady = '1'
  }

  function bindAudioPlayer(card: HTMLElement, mediaId: string, url: string): void {
    const toggleBtn = card.querySelector<HTMLButtonElement>('[data-audio-action="toggle"]')
    const seekBar = card.querySelector<HTMLElement>('[data-audio-action="seek"]')
    const fill = card.querySelector<HTMLElement>('.audio-progress-fill')
    const timeEl = card.querySelector<HTMLElement>('.audio-time')
    const volumeToggle = card.querySelector<HTMLButtonElement>('[data-audio-action="volume-toggle"]')
    const volumeBar = card.querySelector<HTMLElement>('[data-audio-action="volume"]')
    const volumeFill = card.querySelector<HTMLElement>('.audio-volume-fill')
    if (!toggleBtn || !seekBar || !fill || !timeEl || !volumeToggle || !volumeBar || !volumeFill) return

    const existing = players.get(mediaId)
    if (existing) {
      existing.card = card
      existing.toggleBtn = toggleBtn
      existing.seekBar = seekBar
      existing.fill = fill
      existing.timeEl = timeEl
      existing.volumeToggle = volumeToggle
      existing.volumeBar = volumeBar
      existing.volumeFill = volumeFill
      updatePlayIcon(existing)
      return
    }

    const audio = new Audio(url)
    audio.preload = 'metadata'
    const player: AudioPlayer = {
      audio,
      card,
      toggleBtn,
      seekBar,
      fill,
      timeEl,
      volumeToggle,
      volumeBar,
      volumeFill,
      dragging: false,
      volumeDragging: false,
    }

    audio.addEventListener('timeupdate', () => renderProgress(player))
    audio.addEventListener('loadedmetadata', () => {
      timeEl.textContent = `0:00 / ${formatTime(audio.duration)}`
    })
    audio.addEventListener('play', () => {
      toggleBtn.classList.add('is-playing')
    })
    audio.addEventListener('pause', () => {
      toggleBtn.classList.remove('is-playing')
    })
    audio.addEventListener('ended', () => {
      toggleBtn.classList.remove('is-playing')
      renderProgress(player)
    })
    audio.addEventListener('volumechange', () => renderVolume(player))

    seekBar.addEventListener('pointerdown', (e) => {
      player.dragging = true
      seekTo(player, e.clientX)
      const move = (ev: PointerEvent) => {
        if (player.dragging) seekTo(player, ev.clientX)
      }
      const up = () => {
        player.dragging = false
        window.removeEventListener('pointermove', move)
        window.removeEventListener('pointerup', up)
      }
      window.addEventListener('pointermove', move)
      window.addEventListener('pointerup', up)
    })

    volumeBar.addEventListener('pointerdown', (e) => {
      player.volumeDragging = true
      setVolumeFromPointer(player, e.clientY)
      const move = (ev: PointerEvent) => {
        if (player.volumeDragging) setVolumeFromPointer(player, ev.clientY)
      }
      const up = () => {
        player.volumeDragging = false
        window.removeEventListener('pointermove', move)
        window.removeEventListener('pointerup', up)
      }
      window.addEventListener('pointermove', move)
      window.addEventListener('pointerup', up)
    })

    players.set(mediaId, player)
    renderProgress(player)
    renderVolume(player)
  }

  function bindVideoPlayer(card: HTMLElement, mediaId: string, url: string): void {
    const container = card.querySelector<HTMLElement>('.media-video-player')
    if (!container) return
    const existing = videoPlayers.get(mediaId)
    if (existing) {
      if (existing.container?.isConnected) return
      existing.destroy()
      videoPlayers.delete(mediaId)
    }
    const theme = getComputedStyle(document.documentElement).getPropertyValue('--primary').trim() || '#3b82f6'
    const downloadUrl = `${url}${url.includes('?') ? '&' : '?'}download=1`
    const player = new Artplayer({
      container,
      url,
      theme,
      autoplay: false,
      autoSize: true,
      playbackRate: true,
      setting: true,
      pip: true,
      fullscreen: true,
      fullscreenWeb: true,
      lang: 'zh-cn',
      controls: [
        {
          name: 'download',
          position: 'right',
          index: 25,
          tooltip: '下载',
          html: '<svg viewBox="0 0 1024 1024" style="width:1.25rem;height:1.25rem;fill:currentColor"><path d="M853.333333 853.333333a42.666667 42.666667 0 0 1 0 85.333334H170.666667a42.666667 42.666667 0 0 1 0-85.333334h682.666666zM512 85.504a42.666667 42.666667 0 0 1 42.666667 42.666667v515.370666l204.373333-204.373333a42.666667 42.666667 0 0 1 63.914667 56.277333l-3.584 4.010667-277.376 277.546667a42.666667 42.666667 0 0 1-56.32 3.584l-4.010667-3.541334-277.12-276.650666a42.666667 42.666667 0 0 1 56.234667-63.957334l4.010666 3.541334L469.333333 644.096V128.170667a42.666667 42.666667 0 0 1 42.666667-42.666667z"/></svg>',
          click() {
            const a = document.createElement('a')
            a.href = downloadUrl
            a.download = ''
            a.click()
          },
        },
      ],
    })
    player.video.addEventListener('error', () => {
      showVideoUnsupported(card, url)
    })
    player.video.preload = 'metadata'
    videoPlayers.set(mediaId, player)
  }

  function showVideoUnsupported(card: HTMLElement, url: string): void {
    if (card.querySelector('.media-video-unsupported')) return
    const hint = document.createElement('div')
    hint.className = 'media-video-unsupported'
    const text = document.createElement('span')
    text.textContent = '当前浏览器不支持该视频格式，请下载后播放'
    const link = document.createElement('a')
    link.href = url
    link.download = ''
    link.className = 'media-card-download'
    link.textContent = '下载文件'
    hint.appendChild(text)
    hint.appendChild(link)
    card.appendChild(hint)
  }

  function renderProgress(player: AudioPlayer): void {
    const audio = player.audio
    const pct = Number.isFinite(audio.duration) && audio.duration > 0 ? (audio.currentTime / audio.duration) * 100 : 0
    player.fill.style.width = `${pct}%`
    player.timeEl.textContent = `${formatTime(audio.currentTime)} / ${formatTime(audio.duration)}`
  }

  function seekTo(player: AudioPlayer, clientX: number): void {
    const rect = player.seekBar.getBoundingClientRect()
    if (rect.width <= 0) return
    const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
    if (Number.isFinite(player.audio.duration)) {
      player.audio.currentTime = ratio * player.audio.duration
    }
  }

  function togglePlay(player: AudioPlayer): void {
    if (player.audio.paused) {
      void player.audio.play()
    } else {
      player.audio.pause()
    }
  }

  function updatePlayIcon(player: AudioPlayer): void {
    if (player.audio.paused) {
      player.toggleBtn.classList.remove('is-playing')
    } else {
      player.toggleBtn.classList.add('is-playing')
    }
  }

  function setVolumeFromPointer(player: AudioPlayer, clientY: number): void {
    const rect = player.volumeBar.getBoundingClientRect()
    if (rect.height <= 0) return
    const ratio = 1 - Math.min(1, Math.max(0, (clientY - rect.top) / rect.height))
    player.audio.volume = ratio
    player.audio.muted = ratio === 0
  }

  function renderVolume(player: AudioPlayer): void {
    const volume = player.audio.muted ? 0 : player.audio.volume
    player.volumeFill.style.height = `${volume * 100}%`
    player.volumeToggle.classList.toggle('is-muted', volume === 0)
  }

  function formatTime(seconds: number): string {
    if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
    const m = Math.floor(seconds / 60)
    const s = Math.floor(seconds % 60)
    return `${m}:${String(s).padStart(2, '0')}`
  }

  function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  }

  function safeParseMeta(text: string): MediaMeta | null {
    try {
      return JSON.parse(text) as MediaMeta
    } catch {
      return null
    }
  }

  function handleClick(event: MouseEvent): void {
    const target = event.target as HTMLElement
    const toggleBtn = target.closest<HTMLElement>('[data-audio-action="toggle"]')
    if (toggleBtn) {
      const card = toggleBtn.closest<HTMLElement>('.media-card')
      const mediaId = card?.dataset.mediaId
      const player = mediaId ? players.get(mediaId) : undefined
      if (player) togglePlay(player)
      return
    }
    const seekBar = target.closest<HTMLElement>('[data-audio-action="seek"]')
    if (seekBar) {
      const card = seekBar.closest<HTMLElement>('.media-card')
      const mediaId = card?.dataset.mediaId
      const player = mediaId ? players.get(mediaId) : undefined
      if (player && !player.dragging) seekTo(player, event.clientX)
      return
    }
    const volumeBtn = target.closest<HTMLElement>('[data-audio-action="volume-toggle"]')
    if (volumeBtn) {
      const card = volumeBtn.closest<HTMLElement>('.media-card')
      const mediaId = card?.dataset.mediaId
      const player = mediaId ? players.get(mediaId) : undefined
      if (player) player.audio.muted = !player.audio.muted
    }
  }

  function dispose(): void {
    for (const player of players.values()) {
      player.audio.pause()
      player.audio.src = ''
    }
    players.clear()
    for (const player of videoPlayers.values()) {
      player.destroy()
    }
    videoPlayers.clear()
  }

  return { fill, handleClick, dispose }
}
