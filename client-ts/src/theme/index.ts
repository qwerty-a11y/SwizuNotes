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
 * 主题切换接口。
 * 每个主题是一个独立的 CSS 文件（**全部由后端提供**），
 * 文件内定义同一套 CSS 变量（调色盘），切换时替换 <link> 的 href 即可。
 *
 * 深浅模式约定：
 *  - 全局深色主题固定名为 'dark'（isDark(name) 判断）
 *  - 浅色主题是其余任意主题（default/green/mid-autumn...），切换时记录最近一次浅色主题
 *  - 主题可带专属深色变体：文件名为 <浅色名>-dark（如 mid-autumn-dark，
 *    DARK_VARIANTS 映射），深浅切换在该主题与其变体间进行；
 *    无变体的浅色主题深浅切换走全局 dark
 *  - 选择持久化在 localStorage（键 swizu_theme / swizu_light_theme）
 *
 * CSS 来源（themeCssHref）：**全部由后端提供**（GET /api/v1/static-resources/<name>.css，
 * 权限由后端控制：已发布公开，未发布/预发布需预览令牌——前端无令牌即 404）。
 * 深浅变体（dark 与 <浅色名>-dark）同为后端资源：变体 CSS 归属对应浅色主题
 * （跟随主题可见性），全局 dark.css 按普通静态资源公开。
 *
 * 本地仅保留 public/theme/default.css 作为**兜底**：仅当后端 static-resources
 * 请求失败（服务器不可达/默认主题 404）时才切换使用，正常情况一律走后端。
 *
 * 新增主题：在管理页上传（CSS 存后端），无需改动前端文件。
 */

export const THEME_LINK_ID = 'theme-link'

/**
 * 主题切换事件（window 上的 CustomEvent，detail.name = 目标主题名）：
 * applyTheme 换完 <link> href 后派发，作为"主题已切换"的状态通知。
 * （横幅背景图/渐隐渐变的动画由 View Transitions API 承担，见 applyTheme，
 *   不再需要"换 href 前捕捉旧值"的组件级事件。）
 */
export const THEME_CHANGE_EVENT = 'themechange'

const THEME_KEY = 'swizu_theme'
export const LIGHT_THEME_KEY = 'swizu_light_theme'
const DEFAULT_THEME = 'default'
export const DARK_THEME = 'dark'

/**
 * 日期自动切换键（管理页配置的主题按日期生效）：
 *  - swizu_theme_auto：'1' 跟随日期自动主题（默认）；'0' 用户手动选择后暂停跟随
 *  - swizu_theme_manual：自动应用前记住的用户主题（区间结束后回退用）
 * 导航栏主题菜单/深浅按钮的显隐由**已发布主题数**决定（themeStore.publicThemeCount > 0
 * 显示主题菜单并隐藏深浅按钮，深浅切换集成在菜单内），不依赖本地标记。
 */
/**
 * API 基址来自独立模块（src/api/base.ts，与 http.ts 单一来源）。
 * 不能 import http.ts：本模块被 http → router → EditorView 的依赖链间接引用，
 * 顶层访问 http 的绑定会触发循环初始化报错。
 */
import { API_BASE } from '@/api/base'

export const THEME_AUTO_KEY = 'swizu_theme_auto'
export const THEME_MANUAL_KEY = 'swizu_theme_manual'

/** 浅色主题 → 专属深色变体的映射（未列出的浅色主题深浅切换走全局 dark） */
const DARK_VARIANTS: Record<string, string> = {
  'mid-autumn': 'mid-autumn-dark',
}

/** 旧主题名 → 新主题名（blue→default、orange→mid-autumn 重命名迁移，兼容已持久化的旧选择） */
const LEGACY_RENAMES: Record<string, string> = {
  blue: 'default',
  orange: 'mid-autumn',
  'orange-dark': 'mid-autumn-dark',
}

function normalizeName(name: string): string {
  return LEGACY_RENAMES[name] ?? name
}

/** 深色主题判定：全局固定名 dark 或主题专属深色变体（<浅色名>-dark） */
export function isDark(name: string): boolean {
  return name === DARK_THEME || name.endsWith('-dark')
}

/**
 * 主题 CSS 的 href：**全部由后端提供**（GET /api/v1/static-resources/<name>.css），
 * 权限由后端控制——已发布公开；未发布/预发布无预览令牌即 404（前端拿不到）。
 * 深浅变体（dark / <浅色名>-dark）同为后端资源：变体 CSS 归属对应浅色主题
 * （跟随主题可见性），全局 dark.css 按普通静态资源公开。
 */
export function themeCssHref(name: string): string {
  return `${API_BASE}/static-resources/${name}.css`
}

/** 浅色主题的深色去向（有专属变体用变体，否则全局 dark；兼容旧名） */
export function getDarkVariant(name: string): string {
  return DARK_VARIANTS[normalizeName(name)] ?? DARK_THEME
}

/** 是否跟随日期自动主题（默认 true；用户手动切换主题后自动置 false） */
export function isThemeAuto(): boolean {
  return localStorage.getItem(THEME_AUTO_KEY) !== '0'
}

/** 设置是否跟随日期自动主题 */
export function setThemeAuto(auto: boolean): void {
  localStorage.setItem(THEME_AUTO_KEY, auto ? '1' : '0')
}

/**
 * 降级渐变（无 View Transitions 的浏览器）：过渡期间挂在 <html> 上的临时 class
 * （见 main.css 的 .theme-transition 规则）。注意：颜色过渡（0.4s，见 main.css）
 * 从**新主题 CSS 加载应用后**才开始计时，而本 class 从 applyTheme 调用时开始计时
 * ——必须留出加载余量，否则 class 提前移除会让未播完的颜色过渡瞬间跳到终值。
 */
const TRANSITION_CLASS = 'theme-transition'
const TRANSITION_MS = 500

let transitionTimer: ReturnType<typeof setTimeout> | null = null

const DEFAULT_HREF = `${API_BASE}/static-resources/${DEFAULT_THEME}.css`
/** 本地兜底主题 CSS：仅当后端 static-resources 请求失败（服务器不可达/默认主题 404）时使用 */
const LOCAL_DEFAULT_HREF = '/theme/default.css'

/**
 * 主题异常回退：目标 CSS 加载失败（404/网络错误）时切回默认主题。
 * 持久化为默认、暂停日期自动跟随（避免每次启动反复尝试失败主题），
 * 并派发 themechange 同步各组件状态。不递归 applyTheme。
 * 两级回退：先切回后端默认 CSS；若后端请求也失败（当前已是后端默认）则切本地兜底文件。
 */
function fallbackToDefault(link: HTMLLinkElement): void {
  if (link.getAttribute('href') === LOCAL_DEFAULT_HREF) return
  localStorage.setItem(THEME_KEY, DEFAULT_THEME)
  localStorage.setItem(THEME_AUTO_KEY, '0')
  const current = link.getAttribute('href')
  link.setAttribute('href', current === DEFAULT_HREF ? LOCAL_DEFAULT_HREF : DEFAULT_HREF)
  window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail: { name: DEFAULT_THEME } }))
}

/** 读取持久化的当前主题名（无记录时回退默认主题；旧主题名自动映射到新名） */
export function getStoredTheme(): string {
  return normalizeName(localStorage.getItem(THEME_KEY) || DEFAULT_THEME)
}

/**
 * 切换 href 并持久化。
 *
 * 动画机制（View Transitions API，Chrome/Edge 111+、Safari 18+、Firefox 140+）：
 * `document.startViewTransition` 对整页做快照交叉淡化——横幅背景图、底部渐隐渐变、
 * 所有颜色属性统一过渡，无需任何组件级覆盖层。回调里换 href 并等待新主题 CSS
 * 加载完成（API 原生支持异步回调，等 Promise resolve 后才拍新快照）。
 * 不支持该 API 的浏览器降级为颜色属性过渡（theme-transition class），背景图硬切。
 *
 * @param animate 是否播放过渡（initTheme 启动应用时传 false，避免首屏闪烁）
 */
export function applyTheme(name: string, persist = true, animate = true): void {
  name = normalizeName(name)
  // 客户端白名单校验：非法主题名回退默认（后端亦有同名格式校验；防 localStorage 污染拼出异常 URL）
  if (!/^[a-z][a-z0-9-]{0,63}$/.test(name)) {
    name = DEFAULT_THEME
  }
  const link = document.getElementById(THEME_LINK_ID) as HTMLLinkElement | null
  const href = themeCssHref(name)
  const supportsVT =
    typeof document !== 'undefined' && typeof document.startViewTransition === 'function'
  const prefersReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false

  if (animate && supportsVT && !prefersReduced && link && link.href !== href) {
    document.startViewTransition(() => {
      link.href = href
      return new Promise<void>((resolve) => {
        let settled = false
        const done = (): void => {
          if (!settled) {
            settled = true
            resolve()
          }
        }
        const onError = (): void => {
          done()
          // 目标 CSS 加载失败：回退默认（回调 resolve 前已换 href，新快照即默认，无闪烁）
          fallbackToDefault(link)
        }
        link.addEventListener('load', done, { once: true })
        link.addEventListener('error', onError, { once: true })
        // 兜底：load/error 都不触发（极端情况）时也结束等待
        window.setTimeout(done, 1000)
      })
    })
  } else {
    if (animate) {
      // 降级：先加过渡 class 并强制 reflow，确保 transition 属性已生效，再换 href 触发颜色渐变
      document.documentElement.classList.add(TRANSITION_CLASS)
      void document.documentElement.offsetHeight
      if (transitionTimer) clearTimeout(transitionTimer)
      transitionTimer = setTimeout(() => {
        document.documentElement.classList.remove(TRANSITION_CLASS)
      }, TRANSITION_MS)
    }
    if (link) {
      link.href = href
      // 目标 CSS 加载失败：回退默认；加载成功则移除 error 监听（避免监听器随切换累积）
      const onError = (): void => fallbackToDefault(link)
      link.addEventListener('error', onError, { once: true })
      link.addEventListener('load', () => link.removeEventListener('error', onError), { once: true })
    }
  }

  if (persist) {
    localStorage.setItem(THEME_KEY, name)
  }
  window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail: { name } }))
}

/**
 * 启动时应用持久化的主题（不重复写盘、不播放渐变）。
 * 额外做一次异步存在性检查：防闪烁脚本已按 localStorage 设置 href 时，
 * 若目标 CSS 失效（404），浏览器加载失败不会触发后续 error 监听（href 未变），
 * 这里主动 fetch 探测（同源静态文件，无 CORS 问题），失效则回退默认主题。
 */
export async function initTheme(): Promise<void> {
  applyTheme(getStoredTheme(), false, false)
  try {
    // 探测目标 CSS（与 themeCssHref 同源：变体走本地、主题走后端），失效则回退默认主题
    const res = await fetch(themeCssHref(getStoredTheme()))
    if (!res.ok) {
      const link = document.getElementById(THEME_LINK_ID) as HTMLLinkElement | null
      if (link) fallbackToDefault(link)
    }
  } catch {
    // 网络异常忽略（保持本地主题）
  }
}

/**
 * 深浅色切换：
 *  - 当前为深色（全局 dark 或主题专属变体 <浅色名>-dark）→ 回到浅色
 *  - 当前为浅色 → 记住它并切到深色（有专属变体用变体，否则全局 dark）
 * 返回切换后的主题名。
 * 手动切换视为用户主动选择 → 暂停日期自动跟随；`keepAuto=true` 时不暂停
 * （用于"已发布主题数 ≤ 1"场景：主题固定为自动切换，深浅切换不破坏跟随）。
 */
export function toggleTheme(keepAuto = false): string {
  if (!keepAuto) setThemeAuto(false)
  const current = getStoredTheme()
  if (isDark(current)) {
    const light =
      current === DARK_THEME
        ? localStorage.getItem(LIGHT_THEME_KEY) || DEFAULT_THEME
        : current.replace(/-dark$/, '')
    applyTheme(light)
    return light
  }
  localStorage.setItem(LIGHT_THEME_KEY, current)
  applyTheme(getDarkVariant(current))
  return getDarkVariant(current)
}

/**
 * 从主题选择器选择浅色主题（保持当前深浅模式；深色时用其专属变体或全局 dark）。
 * 手动选择 → 暂停日期自动跟随。
 */
export function selectLightTheme(name: string): void {
  const current = getStoredTheme()
  if (isDark(current)) {
    localStorage.setItem(LIGHT_THEME_KEY, name)
    applyTheme(getDarkVariant(name))
  } else {
    applyTheme(name)
  }
  setThemeAuto(false)
}

/**
 * 日期自动跟随：启动时拉取"今天生效的日期主题"（管理页配置）并应用。
 *  - 有生效主题且处于自动跟随模式 → 应用（自动前记住当前主题，供区间结束后回退）
 *  - 无生效主题且处于自动跟随模式 → 若之前被自动应用过，回退到记住的主题
 *  - 用户手动选过主题（auto=0）→ 一律不覆盖
 * 网络失败静默（保持本地主题）。
 */
export async function initThemeAuto(): Promise<void> {
  let activeName: string | null = null
  try {
    const { getActiveTheme } = await import('@/api/theme')
    activeName = (await getActiveTheme()).data?.name ?? null
  } catch {
    return
  }
  if (!activeName) {
    const manual = localStorage.getItem(THEME_MANUAL_KEY)
    if (isThemeAuto() && manual && getStoredTheme() !== manual) {
      applyTheme(manual, true, false)
      localStorage.removeItem(THEME_MANUAL_KEY)
    }
    return
  }
  if (!isThemeAuto()) return
  const current = getStoredTheme()
  if (current === activeName) return
  if (!localStorage.getItem(THEME_MANUAL_KEY)) {
    localStorage.setItem(THEME_MANUAL_KEY, current)
  }
  applyTheme(activeName, true, false)
}
