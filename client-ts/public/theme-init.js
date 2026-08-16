// 防闪烁：CSS 加载前按持久化主题设置 href（外置脚本，配合 CSP script-src 'self' 无内联）
// 主题 CSS（含深浅变体 dark / *-dark）全部由后端提供（static-resources，权限控制：
// 未发布/预发布主题无令牌即 404，前端拿不到）
;(function () {
  var t = localStorage.getItem('swizu_theme')
  if (t) {
    var link = document.getElementById('theme-link')
    if (link) link.href = '/api/v1/static-resources/' + t + '.css'
  }
})()
