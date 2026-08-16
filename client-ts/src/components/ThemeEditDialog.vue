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
 * 主题编辑弹窗：资源（banner 图悬停更换 + CSS 上传/下载）与配置编辑一体。
 * 资源区分云端有/无：banner 有图显示预览（悬停更换），无图显示上传虚线框；
 * CSS 显示"已上传/未上传"状态徽标 + 对应操作。未发布/预发布主题自动申请预览令牌。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { API_BASE } from '@/api/http'
import { createPreviewToken, updateTheme } from '@/api/theme'
import AppIcon from '@/components/AppIcon.vue'
import MediaCoverPicker from '@/components/MediaCoverPicker.vue'
import { toast } from '@/utils/toast'
import type { ThemeAdmin, ThemeStatus } from '@/types/theme'

const props = defineProps<{ theme: ThemeAdmin }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const LUNAR_PATTERN = /^(闰)?(1[0-2]|[1-9])-([1-9]|[12][0-9]|30)$/

const STATUS_OPTIONS: { value: ThemeStatus; label: string }[] = [
  { value: 'unpublished', label: '未发布' },
  { value: 'prerelease', label: '预发布' },
  { value: 'published', label: '已发布' },
]

// ============ 资源状态（云端有/无） ============

const previewToken = ref('')
/** 资源检查完成（完成后才渲染 MediaCoverPicker，保证 initialSrc 正确） */
const resourcesChecked = ref(false)
const lightUrl = ref('')
const darkUrl = ref('')
/** CSS 是否已在云端（存在时 cssText 供下载） */
const cssAvailable = ref(false)
const cssText = ref('')
const resourceError = ref('')

function resourceUrl(fileName: string): string {
  const token = previewToken.value
  return `${API_BASE}/static-resources/${fileName}${token ? `?preview_token=${token}` : ''}`
}

async function loadResources(): Promise<void> {
  try {
    if (props.theme.status !== 'published') {
      previewToken.value = (await createPreviewToken(props.theme.name)).data.token
    }
    // banner：云端有无直接按后端返回的登记字段判断
    // （bannerLight/bannerDark 非空 = static_resources 有登记；文件缺失的极端情况由图片加载兜底）
    if (props.theme.bannerLight) lightUrl.value = resourceUrl(props.theme.bannerLight)
    if (props.theme.bannerDark) darkUrl.value = resourceUrl(props.theme.bannerDark)
    // CSS：GET 拉取（成功 = 云端有，内容同时供下载；失败 = 云端缺失）
    try {
      const res = await fetch(resourceUrl(`${props.theme.name}.css`))
      if (res.ok) {
        cssText.value = await res.text()
        cssAvailable.value = true
      }
    } catch {
      // 视为云端缺失
    }
  } catch (e) {
    resourceError.value = (e as Error).message
  } finally {
    resourcesChecked.value = true
  }
}

function downloadCss(): void {
  const blob = new Blob([cssText.value], { type: 'text/css' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${props.theme.name}.css`
  a.click()
  URL.revokeObjectURL(url)
}

// ============ 编辑表单 ============

const form = reactive({
  displayName: props.theme.displayName,
  startDate: props.theme.startDate ?? '',
  endDate: props.theme.endDate ?? '',
  lunarStart: props.theme.lunarStart ?? '',
  lunarEnd: props.theme.lunarEnd ?? '',
  status: props.theme.status,
  /** 新选择的 CSS 文件（保存时上传；null = 保留云端现状） */
  css: null as File | null,
  /** 新选择的亮色 banner（MediaCoverPicker v-model） */
  bannerLightFile: null as File | null,
  /** 新选择的暗色 banner */
  bannerDarkFile: null as File | null,
})
const saving = ref(false)
const error = ref('')

function onCssPick(event: Event): void {
  const input = event.target as HTMLInputElement
  form.css = input.files?.[0] ?? null
  input.value = ''
}

/** 日期自动切换模式：无 / 仅公历 / 仅农历（三选一，互斥，不允许同时配置） */
type DateMode = 'none' | 'solar' | 'lunar'

/** 默认主题（name = default）为内置兜底主题：不参与自动轮换，日期模式固定"无"且不可配置 */
const isDefault = computed(() => props.theme.name === 'default')

/** 初始模式按主题现有配置推断（有公历区间 = 公历；有农历区间 = 农历；都没有 = 无；default 固定"无"） */
const dateMode = ref<DateMode>(
  isDefault.value
    ? 'none'
    : props.theme.startDate || props.theme.endDate
      ? 'solar'
      : props.theme.lunarStart || props.theme.lunarEnd
        ? 'lunar'
        : 'none',
)

/** 切换模式时清空另一组的残留输入（避免误提交混合配置）；default 恒为"无" */
watch(dateMode, (mode) => {
  if (isDefault.value) {
    dateMode.value = 'none'
    return
  }
  if (mode === 'none') {
    form.startDate = ''
    form.endDate = ''
    form.lunarStart = ''
    form.lunarEnd = ''
  } else if (mode === 'solar') {
    form.lunarStart = ''
    form.lunarEnd = ''
  } else {
    form.startDate = ''
    form.endDate = ''
  }
})

async function submit(): Promise<void> {
  error.value = ''
  if (!form.displayName.trim()) {
    error.value = '显示名不能为空'
    return
  }
  if (dateMode.value === 'solar') {
    if (!form.startDate || !form.endDate) {
      error.value = '公历区间需同时填写开始与结束日期（可为同一天）'
      return
    }
    if (form.startDate > form.endDate) {
      error.value = '开始日期不能晚于结束日期'
      return
    }
  }
  if (dateMode.value === 'lunar') {
    if (!form.lunarStart.trim() || !form.lunarEnd.trim()) {
      error.value = '农历区间需同时填写开始与结束日期（可为同一天）'
      return
    }
    if (!LUNAR_PATTERN.test(form.lunarStart.trim()) || !LUNAR_PATTERN.test(form.lunarEnd.trim())) {
      error.value = '农历日期格式：M-d 或 闰M-d（如 8-15、闰8-15）'
      return
    }
  }
  if (form.status === 'prerelease' && dateMode.value === 'none') {
    error.value = '预发布状态需配置日期区间（公历或农历），否则无法自动生效'
    return
  }
  // 预发布/已发布必须有名有封面（亮+暗）：封面 = 云端已有登记或本次新选文件
  if (form.status !== 'unpublished') {
    if (!props.theme.bannerLight && !form.bannerLightFile) {
      error.value = '预发布/已发布需先上传亮色封面背景图'
      return
    }
    if (!props.theme.bannerDark && !form.bannerDarkFile) {
      error.value = '预发布/已发布需先上传暗色封面背景图'
      return
    }
  }
  saving.value = true
  try {
    // banner 上传前浏览器转 webp（canvas 编码 q0.9，节省网络流量；已是 webp 或转换失败降级原格式）
    const bannerLightFile = form.bannerLightFile ? await toWebpFile(form.bannerLightFile) : null
    const bannerDarkFile = form.bannerDarkFile ? await toWebpFile(form.bannerDarkFile) : null
    await updateTheme(props.theme.name, {
      displayName: form.displayName.trim(),
      startDate: dateMode.value === 'solar' ? form.startDate : undefined,
      endDate: dateMode.value === 'solar' ? form.endDate : undefined,
      lunarStart: dateMode.value === 'lunar' ? form.lunarStart.trim() : undefined,
      lunarEnd: dateMode.value === 'lunar' ? form.lunarEnd.trim() : undefined,
      status: form.status,
      css: form.css ?? undefined,
      bannerLight: bannerLightFile ?? undefined,
      bannerDark: bannerDarkFile ?? undefined,
    })
    emit('saved')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

/**
 * 图片文件 → webp（canvas 编码，quality 0.9）：
 * 已是 webp 直接返回；浏览器不支持编码或转换失败时降级返回原文件（warn 提示，不阻断上传）。
 */
async function toWebpFile(file: File): Promise<File> {
  if (file.type === 'image/webp' || file.name.toLowerCase().endsWith('.webp')) return file
  try {
    const bitmap = await createImageBitmap(file)
    try {
      const canvas = document.createElement('canvas')
      canvas.width = bitmap.width
      canvas.height = bitmap.height
      const ctx = canvas.getContext('2d')
      if (!ctx) return file
      ctx.drawImage(bitmap, 0, 0)
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', 0.9))
      if (!blob) {
        toast.warn('当前浏览器不支持 webp 编码，banner 已按原格式上传')
        return file
      }
      return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.webp', { type: 'image/webp' })
    } finally {
      bitmap.close()
    }
  } catch {
    toast.warn('banner 转 webp 失败，已按原格式上传')
    return file
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  void loadResources()
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="dialog-mask" @click.self="emit('close')">
    <div class="dialog" role="dialog" aria-modal="true">
      <div class="dialog-head">
        <span class="dialog-title">
          编辑主题：{{ theme.displayName }}
          <code class="dialog-id">{{ theme.name }}</code>
        </span>
        <button class="dialog-close" type="button" aria-label="关闭" @click="emit('close')">
          <AppIcon name="close" />
        </button>
      </div>

      <p v-if="resourceError" class="form-error">{{ resourceError }}</p>

      <!-- ===== 背景图（悬停更换；云端缺失/未上传显示上传虚线框） ===== -->
      <div class="resource-section">
        <div class="resource-title">背景图（banner）</div>
        <div v-if="!resourcesChecked" class="muted">检查云端资源…</div>
        <div v-else class="banner-grid">
          <div class="banner-item">
            <div class="banner-caption">亮色</div>
            <!-- 主题 banner 必须存在（预发布/已发布硬约束）：仅可更换，不可移除 -->
            <MediaCoverPicker v-model="form.bannerLightFile" :initial-src="lightUrl" :removable="false" />
          </div>
          <div class="banner-item">
            <div class="banner-caption">暗色</div>
            <MediaCoverPicker v-model="form.bannerDarkFile" :initial-src="darkUrl" :removable="false" />
          </div>
        </div>
      </div>

      <!-- ===== CSS（区分云端有/无） ===== -->
      <div class="resource-section">
        <div class="resource-title">CSS 调色盘</div>
        <div v-if="!resourcesChecked" class="muted">检查云端资源…</div>
        <div v-else class="css-status">
          <span class="badge" :class="cssAvailable ? 'badge-on' : 'badge-off'">
            {{ cssAvailable ? '已上传' : '未上传' }}
          </span>
          <code class="css-filename">{{ theme.name }}.css</code>
          <div class="css-actions">
            <template v-if="cssAvailable">
              <button type="button" class="btn small" @click="downloadCss">
                <AppIcon name="download" />
                下载
              </button>
              <label class="btn small">
                更换
                <input type="file" accept=".css,text/css" @change="onCssPick" />
              </label>
            </template>
            <label v-else class="btn small primary">
              上传 CSS
              <input type="file" accept=".css,text/css" @change="onCssPick" />
            </label>
          </div>
        </div>
        <p v-if="form.css" class="pending-hint">已选择新文件：{{ form.css.name }}（保存后上传）</p>
      </div>

      <!-- ===== 编辑配置 ===== -->
      <div class="resource-section">
        <div class="resource-title">编辑配置</div>
        <form class="theme-form" @submit.prevent="submit">
          <div class="form-grid">
            <label class="field">
              <span class="field-label">显示名</span>
              <input v-model="form.displayName" type="text" />
            </label>
            <label class="field">
              <span class="field-label">日期自动切换</span>
              <div class="date-mode-group">
                <label class="date-mode-option" :class="{ checked: dateMode === 'none' }">
                  <input v-model="dateMode" type="radio" value="none" :disabled="isDefault" />
                  无
                </label>
                <label class="date-mode-option" :class="{ checked: dateMode === 'solar' }">
                  <input v-model="dateMode" type="radio" value="solar" :disabled="isDefault" />
                  公历
                </label>
                <label class="date-mode-option" :class="{ checked: dateMode === 'lunar' }">
                  <input v-model="dateMode" type="radio" value="lunar" :disabled="isDefault" />
                  农历
                </label>
              </div>
              <span v-if="isDefault" class="field-hint">默认主题固定使用默认样式，不参与日期自动切换</span>
            </label>
            <template v-if="dateMode === 'solar'">
              <label class="field">
                <span class="field-label">公历开始</span>
                <input v-model="form.startDate" type="date" />
              </label>
              <label class="field">
                <span class="field-label">公历结束</span>
                <input v-model="form.endDate" type="date" />
              </label>
            </template>
            <template v-else-if="dateMode === 'lunar'">
              <label class="field">
                <span class="field-label">农历开始</span>
                <input v-model="form.lunarStart" type="text" placeholder="如 8-15" />
              </label>
              <label class="field">
                <span class="field-label">农历结束</span>
                <input v-model="form.lunarEnd" type="text" placeholder="如 8-17" />
              </label>
            </template>
            <label class="field status-field">
              <span class="field-label">发布状态</span>
              <select v-model="form.status" class="status-select" :disabled="isDefault">
                <option v-for="opt in STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
              <span v-if="isDefault" class="field-hint">默认主题固定为已发布，不可更改</span>
            </label>
          </div>

          <p v-if="error" class="form-error">{{ error }}</p>

          <div class="form-actions">
            <button type="submit" class="btn primary" :disabled="saving">
              {{ saving ? '保存中…' : '保存' }}
            </button>
            <button type="button" class="btn" @click="emit('close')">取消</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 400;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: var(--overlay-bg);
}

.dialog {
  width: min(44rem, 100%);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  padding: 1.1rem 1.3rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-lift);
  overflow-y: auto;
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.8rem;
}

.dialog-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-strong);
}

.dialog-id {
  font-size: 0.78rem;
  color: var(--text-faint);
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0.1rem 0.4rem;
}

.dialog-close {
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

.dialog-close:hover {
  color: var(--primary);
  background: var(--primary-soft);
}

.dialog-close svg {
  width: 1.2rem;
  height: 1.2rem;
  fill: currentColor;
}

/* 资源区 */
.resource-section {
  margin-bottom: 1rem;
}

.resource-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  margin-bottom: 0.5rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text);
}

.banner-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.8rem;
}

.banner-item {
  position: relative;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 0.4rem;
  background: var(--bg-input);
}

.banner-caption {
  display: inline-block;
  margin-bottom: 0.35rem;
  padding: 0.1rem 0.5rem;
  font-size: 0.72rem;
  color: var(--text-muted);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 999px;
}

/* CSS 状态行 */
.css-status {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.6rem 0.8rem;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 10px;
}

.badge {
  font-size: 0.72rem;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  white-space: nowrap;
}

.badge-on {
  color: var(--primary);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-border);
}

.badge-off {
  color: var(--text-muted);
  background: var(--bg-muted);
  border: 1px solid var(--border);
}

.css-filename {
  font-size: 0.8rem;
  color: var(--text-faint);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0.1rem 0.4rem;
}

.css-actions {
  margin-left: auto;
  display: flex;
  gap: 0.5rem;
}

.pending-hint {
  margin: 0.4rem 0 0;
  font-size: 0.8rem;
  color: var(--primary);
}

/* 表单 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.8rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.field-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text);
}

.field input[type='text'],
.field input[type='date'],
.status-select {
  padding: 0.5rem 0.7rem;
  font-family: inherit;
  font-size: 0.9rem;
  color: var(--text);
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 8px;
  outline: none;
  transition: border-color 0.15s ease;
}

.field input:focus,
.status-select:focus {
  border-color: var(--primary);
}

.status-select {
  cursor: pointer;
}

.status-field {
  max-width: 14rem;
}

/* 日期自动切换模式选择（三选一胶囊） */
.date-mode-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.date-mode-option {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.35rem 0.75rem;
  font-size: 0.85rem;
  color: var(--text);
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 999px;
  cursor: pointer;
  user-select: none;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.date-mode-option:hover {
  color: var(--primary);
  border-color: var(--primary);
}

.date-mode-option.checked {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.date-mode-option input {
  margin: 0;
  accent-color: var(--primary);
}

.date-mode-option:has(input:disabled) {
  opacity: 0.55;
  cursor: not-allowed;
}

.field-hint {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.form-actions {
  display: flex;
  gap: 0.6rem;
  margin-top: 1rem;
}

.form-error {
  color: var(--danger);
  font-size: 0.88rem;
  margin: 0 0 0.6rem;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.85rem;
  font-family: inherit;
  font-size: 0.82rem;
  color: var(--text);
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.btn:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.btn.primary {
  color: var(--on-primary);
  background: var(--primary);
  border-color: var(--primary);
}

.btn.primary:hover {
  background: var(--primary-hover);
  border-color: var(--primary-hover);
  color: var(--on-primary);
}

.btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.small {
  padding: 0.3rem 0.7rem;
}

.btn input[type='file'] {
  display: none;
}

.btn svg {
  width: 0.95rem;
  height: 0.95rem;
  fill: currentColor;
}

.muted {
  color: var(--text-faint);
  font-size: 0.85rem;
}
</style>
