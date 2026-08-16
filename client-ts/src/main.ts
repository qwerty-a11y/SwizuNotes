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

import './assets/main.css'
import 'viewerjs/dist/viewer.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initTheme, initThemeAuto } from './theme'
import { useThemeStore } from './stores/theme'

// 应用持久化的主题（index.html 内联脚本已防闪烁，这里兜底；含失效主题的存在性检查回退）。
// 启动链（见文件尾部）：initTheme 的异步探测完成后再走日期自动跟随与预览恢复——
// 否则探测失败回退默认主题会覆盖 restorePreview 刚设置的预览 blob。

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.mount('#app')

// 串行启动链：持久主题（含失效探测）→ 日期自动跟随 → 预览恢复（预览优先于日期主题）。
// 均在挂载后异步执行，不阻塞首屏。
void initTheme().finally(() => {
  void initThemeAuto().finally(() => {
    // 预览会话恢复（跨刷新持久；放在日期主题之后，预览优先）
    useThemeStore(pinia).restorePreview()
  })
})
