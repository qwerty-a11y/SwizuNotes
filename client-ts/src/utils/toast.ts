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

/**
 * 全局顶部 toast 通知（页面内不再插入提示文本）。
 * 三级：info（信息）/ warn（警告）/ error（错误），不同颜色（见 ToastHost.vue）。
 * 任意位置直接调用 toast.info/warn/error(message)；自动消失（点击也可关闭），
 * 由 App.vue 挂载的 ToastHost.vue 渲染（模块级响应式状态，无 provide/inject 依赖）。
 */
import { reactive } from 'vue'

export type ToastLevel = 'info' | 'warn' | 'error'

export interface ToastItem {
  id: number
  level: ToastLevel
  message: string
}

const toasts = reactive<ToastItem[]>([])
let nextId = 1

/** 各等级自动消失时长（毫秒） */
const DURATION: Record<ToastLevel, number> = {
  info: 3000,
  warn: 4000,
  error: 5000,
}

function push(level: ToastLevel, message: string): void {
  const id = nextId++
  toasts.push({ id, level, message })
  window.setTimeout(() => dismiss(id), DURATION[level])
}

/** 手动关闭指定 toast（点击/超时共用） */
export function dismiss(id: number): void {
  const index = toasts.findIndex((t) => t.id === id)
  if (index >= 0) toasts.splice(index, 1)
}

/** 顶部 toast 通知（全局唯一入口）：toast.info / toast.warn / toast.error */
export const toast = {
  info(message: string): void {
    push('info', message)
  },
  warn(message: string): void {
    push('warn', message)
  },
  error(message: string): void {
    push('error', message)
  },
}

/** ToastHost 渲染用：响应式 toast 列表 + 关闭函数 */
export function useToasts(): { toasts: ToastItem[]; dismiss: (id: number) => void } {
  return { toasts, dismiss }
}
