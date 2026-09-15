<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

onLaunch(() => {
  console.log('[V-POKER] App Launch')
  // #ifdef H5
  setupPortraitTip()
  // #endif
})
onShow(() => {})
onHide(() => {})

// #ifdef H5
// H5 端浏览器无法像 App 一样强制横屏：竖屏时全屏提示，引导横置设备或拉宽窗口
function setupPortraitTip() {
  if (window.__PK_FL_STAGE__) return
  if (document.getElementById('pk-portrait-tip')) return
  const tip = document.createElement('div')
  tip.id = 'pk-portrait-tip'
  tip.style.cssText = 'position:fixed;inset:0;z-index:99999;display:none;flex-direction:column;align-items:center;justify-content:center;gap:16px;background:#0B1A3A;color:#E6C25A;font-size:18px;text-align:center;padding:24px;'
  tip.innerHTML =
    '<div style="font-size:56px">📱</div>' +
    '<div>请将设备横屏 / 拉宽浏览器窗口</div>' +
    '<div style="font-size:13px;color:#9AA8CC">V-POKER 为横屏 16:9 游戏</div>'
  document.body.appendChild(tip)
  const update = () => {
    tip.style.display = window.innerHeight > window.innerWidth ? 'flex' : 'none'
  }
  update()
  window.addEventListener('resize', update)
  window.addEventListener('orientationchange', update)
}
// #endif
</script>

<style lang="scss">
@import "@/styles/tokens/index.scss";

page {
  background: $color-bg-page;
  color: $color-text-primary;
  font-family: $font-family-base;
  font-size: $font-size-base;
  min-height: 100vh;
}

view, text, button {
  box-sizing: border-box;
}
</style>
