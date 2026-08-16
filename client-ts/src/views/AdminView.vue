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
 * 管理页 —— 主题管理：
 *  - 上传新主题（CSS 调色盘 + 亮/暗两张 banner 背景图 + 显示名/日期区间/公开状态）
 *  - 日期自动切换配置（开始/结束日期，区间内自动生效）
 *  - 预览未公开主题（预览令牌 + blob CSS 应用当前页面，仅本管理员可见）
 *  - 公开/下架、编辑、删除
 */
import { onMounted, reactive, ref } from 'vue'
import {
  createTheme,
  deleteTheme,
  getActiveTheme,
  getAdminThemes,
  updateTheme,
} from '@/api/theme'
import AppIcon from '@/components/AppIcon.vue'
import ThemeEditDialog from '@/components/ThemeEditDialog.vue'
import { useThemeStore } from '@/stores/theme'
import { toast } from '@/utils/toast'
import type { ThemeAdmin, ThemeSummary, ThemeStatus } from '@/types/theme'

const themeStore = useThemeStore()

const NAME_PATTERN = /^[a-z][a-z0-9-]{0,63}$/

/** 三态选项（列表状态徽标文案用） */
const STATUS_OPTIONS: { value: ThemeStatus; label: string }[] = [
  { value: 'unpublished', label: '未发布' },
  { value: 'prerelease', label: '预发布' },
  { value: 'published', label: '已发布' },
]

/** 占位符说明文本（模板中直接写 {{ 会与插值语法冲突，故用变量） */
const BANNER_PLACEHOLDERS = '{{BANNER_LIGHT}} / {{BANNER_DARK}}'

const themes = ref<ThemeAdmin[]>([])
const activeTheme = ref<ThemeSummary | null>(null)
const loading = ref(true)

/** 是否无管理员权限（403） */
const forbidden = ref(false)

// ============ 新建主题（先建空壳 → 编辑弹窗内上传资源/配置） ============

const createForm = reactive({
  name: '',
  displayName: '',
})
const creating = ref(false)

async function submitCreate(): Promise<void> {
  if (!NAME_PATTERN.test(createForm.name)) {
    toast.error('主题名需为小写字母开头的字母/数字/连字符组合（最长 64 位）')
    return
  }
  if (!createForm.displayName.trim()) {
    toast.error('请填写显示名')
    return
  }
  creating.value = true
  try {
    const { data } = await createTheme({
      name: createForm.name.trim(),
      displayName: createForm.displayName.trim(),
      status: 'unpublished',
    })
    const created = data
    createForm.name = ''
    createForm.displayName = ''
    toast.info(`主题「${created.displayName}」已创建，请上传 CSS 与背景图并配置`)
    await load()
    // 新建成功后直接进入编辑弹窗进行后续操作（上传资源/配置）
    editTarget.value = created
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    creating.value = false
  }
}

// ============ 编辑表单 ============

/** 编辑弹窗（资源预览 + 配置编辑一体）当前主题 */
const editTarget = ref<ThemeAdmin | null>(null)

// ============ 删除 ============

async function remove(t: ThemeAdmin): Promise<void> {
  if (!window.confirm(`确定删除主题「${t.displayName}」吗？CSS、背景图与配置将一并删除，不可恢复。`)) {
    return
  }
  try {
    await deleteTheme(t.name)
    toast.info(`主题「${t.displayName}」已删除`)
    if (themeStore.preview?.name === t.name) themeStore.exitPreview()
    await load()
  } catch (e) {
    toast.error((e as Error).message)
  }
}

// ============ 预览未公开主题 ============

/**
 * 预览（持久，跨路由/刷新保持）：令牌 → fetch CSS（未发布时后端已把 url() 重写为带令牌地址）
 * → blob URL 应用到 <link id="theme-link"> 整站切换；会话存 themeStore + localStorage，
 * 任意页面底部浮条可退出预览。
 */
async function previewTheme(t: ThemeAdmin): Promise<void> {
  try {
    await themeStore.startPreview(t.name, t.displayName, t.status === 'published')
    toast.info(
      t.status === 'published'
        ? `正在预览主题「${t.displayName}」（仅你可见）`
        : `正在预览主题「${t.displayName}」（未发布，仅你可见）`,
    )
  } catch (e) {
    toast.error((e as Error).message)
  }
}

// ============ 加载 ============

async function load(): Promise<void> {
  loading.value = true
  try {
    const [themesRes, activeRes] = await Promise.all([getAdminThemes(), getActiveTheme()])
    themes.value = themesRes.data
    activeTheme.value = activeRes.data
  } catch (e) {
    const message = (e as Error).message
    if (message.includes('403') || message.includes('无权限') || message.includes('ADMIN')) {
      forbidden.value = true
    } else {
      toast.error(message)
    }
  } finally {
    loading.value = false
  }
}

function formatDate(d: string | null): string {
  return d ?? ''
}

function formatRange(t: ThemeAdmin): string {
  const parts: string[] = []
  if (t.startDate || t.endDate) {
    if (t.startDate && t.endDate) parts.push(`公历 ${t.startDate} ~ ${t.endDate}`)
    else if (t.startDate) parts.push(`公历 ${t.startDate} 起`)
    else parts.push(`公历至 ${t.endDate}`)
  }
  if (t.lunarStart) {
    parts.push(t.lunarEnd ? `农历 ${t.lunarStart} ~ ${t.lunarEnd}` : `农历 ${t.lunarStart}（单日）`)
  }
  return parts.length ? parts.join('　·　') : '未配置'
}

function statusLabel(status: ThemeStatus): string {
  return STATUS_OPTIONS.find((o) => o.value === status)?.label ?? status
}

function statusBadgeClass(status: ThemeStatus): string {
  return status === 'published' ? 'badge-on' : status === 'prerelease' ? 'badge-prerelease' : 'badge-off'
}

onMounted(() => {
  void load()
})

/** 编辑保存成功：关闭弹窗、提示并刷新列表 */
function onEdited(): void {
  toast.info(`主题「${editTarget.value?.displayName}」已更新`)
  editTarget.value = null
  void load()
}
</script>

<template>
  <section class="admin">
    <h1>管理</h1>
    <p class="page-hint">主题管理：上传主题、配置日期自动切换、预览未公开主题。</p>

    <!-- 权限不足 -->
    <div v-if="forbidden" class="card">
      <p class="forbidden">无管理员权限，无法访问主题管理。</p>
    </div>

    <template v-else>
      <!-- 当前生效 -->
      <div class="card active-card">
        <h2>日期自动切换</h2>
        <p class="card-hint">
          配置公历或农历开始/结束日期后，区间内（含边界）预发布与已发布主题自动生效。农历每年重复
          （格式 M-d，闰月加"闰"前缀如 闰8-15；支持跨年区间如 12-25 ~ 1-5）。
          多个主题区间重叠时，<strong>后上传的优先</strong>。
          已发布主题普通用户可随时切换（主题菜单）；预发布主题生效前按未发布处理，<strong>首次生效自动转为已发布</strong>。
          用户手动切换过主题后自动跟随暂停。
        </p>
        <p class="active-line">
          今日生效：
          <strong v-if="activeTheme">{{ activeTheme.displayName }}</strong>
          <span v-else class="muted">无（使用默认主题）</span>
        </p>
      </div>

      <!-- 新建主题（两步式：先建空壳，再在编辑弹窗上传资源/配置） -->
      <div class="card">
        <h2>新建主题</h2>
        <p class="card-hint">
          输入主题 id 与显示名创建空主题，创建成功后自动打开编辑弹窗——
          在那里上传 CSS（含 {{ BANNER_PLACEHOLDERS }} 占位符）与亮/暗背景图、配置日期与发布状态。
        </p>
        <form class="create-form" @submit.prevent="submitCreate">
          <label class="field">
            <span class="field-label">主题 id（URL 标识）</span>
            <input v-model="createForm.name" type="text" required placeholder="如 mid-autumn" />
          </label>
          <label class="field">
            <span class="field-label">显示名</span>
            <input v-model="createForm.displayName" type="text" required placeholder="如 中秋" />
          </label>
          <button type="submit" class="btn primary" :disabled="creating">
            {{ creating ? '创建中…' : '创建主题' }}
          </button>
        </form>
        <p class="create-hint">主题 id 需为小写字母开头，字母/数字/连字符，最长 64 位，创建后不可修改。</p>
      </div>

      <!-- 主题列表 -->
      <div class="card">
        <h2>主题列表</h2>
        <p v-if="loading" class="muted">加载中…</p>
        <p v-else-if="themes.length === 0" class="muted">暂无主题，上传一个吧。</p>
        <div v-else class="theme-list">
          <div v-for="t in themes" :key="t.name" class="theme-row">
            <div class="theme-row-main">
              <div class="theme-row-title">
                <span class="theme-row-name">{{ t.displayName }}</span>
                <code class="theme-row-id">{{ t.name }}</code>
                <span class="badge" :class="statusBadgeClass(t.status)">{{ statusLabel(t.status) }}</span>
                <span v-if="activeTheme && activeTheme.name === t.name" class="badge badge-active">今日生效</span>
              </div>
              <div class="theme-row-meta">
                自动切换：{{ formatRange(t) }}　·　创建于 {{ formatDate(t.createdAt.slice(0, 10)) }}
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="theme-row-actions">
              <button type="button" class="btn" @click="previewTheme(t)">
                <AppIcon name="eye" />
                预览
              </button>
              <button type="button" class="btn" @click="editTarget = t">编辑</button>
              <button
                type="button"
                class="btn danger"
                :disabled="t.name === 'default'"
                :title="t.name === 'default' ? '默认主题不可删除' : ''"
                @click="remove(t)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 编辑弹窗（资源预览 + 配置编辑一体） -->
    <ThemeEditDialog v-if="editTarget" :theme="editTarget" @close="editTarget = null" @saved="onEdited" />
  </section>
</template>

<style scoped>
.admin {
  max-width: 52rem;
  margin: 0 auto;
  padding: 2rem 1rem 3rem;
}

h1 {
  margin: 0 0 0.3rem;
  font-size: 1.5rem;
  color: var(--text-strong);
}

.page-hint {
  margin: 0 0 1.2rem;
  color: var(--text-faint);
  font-size: 0.9rem;
}

.card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 1.2rem 1.4rem;
  margin-bottom: 1.2rem;
  box-shadow: var(--shadow-card);
}

.card h2 {
  margin: 0 0 0.6rem;
  font-size: 1.05rem;
  color: var(--text-strong);
}

.card-hint {
  margin: 0 0 0.6rem;
  color: var(--text-muted);
  font-size: 0.85rem;
  line-height: 1.6;
}

.active-line {
  margin: 0;
  font-size: 0.95rem;
  color: var(--text);
}

.active-line strong {
  color: var(--primary);
}

.forbidden {
  color: var(--danger);
  margin: 0;
}

/* 表单 */
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
.field input[type='date'] {
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

.field input:focus {
  border-color: var(--primary);
}

.field-hint {
  font-style: normal;
  font-size: 0.75rem;
  color: var(--text-faint);
  line-height: 1.5;
}

/* 新建主题两步式表单 */
.create-form {
  display: flex;
  align-items: flex-end;
  gap: 0.8rem;
  flex-wrap: wrap;
}

.create-form .field {
  flex: 1 1 14rem;
}

.create-form .btn {
  flex-shrink: 0;
}

.create-hint {
  margin: 0.6rem 0 0;
  font-style: normal;
  font-size: 0.75rem;
  color: var(--text-faint);
  line-height: 1.5;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.9rem;
  font-family: inherit;
  font-size: 0.85rem;
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

.btn.danger {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 40%, transparent);
}

.btn.danger:hover {
  background: var(--danger-soft);
  border-color: var(--danger);
  color: var(--danger);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.btn svg {
  width: 0.95rem;
  height: 0.95rem;
  fill: currentColor;
}

/* 主题列表 */
.theme-list {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.theme-row {
  padding: 0.9rem 1rem;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg-input);
}

.theme-row-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.theme-row-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.theme-row-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-strong);
}

.theme-row-id {
  font-size: 0.8rem;
  color: var(--text-faint);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0.1rem 0.4rem;
}

.theme-row-meta {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.theme-row-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.7rem;
  flex-wrap: wrap;
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

.badge-prerelease {
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fcd34d;
}

.badge-off {
  color: var(--text-muted);
  background: var(--bg-muted);
  border: 1px solid var(--border);
}

.badge-active {
  color: var(--danger);
  background: var(--danger-soft);
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent);
}

.muted {
  color: var(--text-faint);
}
</style>
