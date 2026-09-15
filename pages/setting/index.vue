<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">设置</text>
    </view>

    <view class="cols">
      <PkCard class="col">
        <view v-for="s in switches" :key="s.key" class="row">
          <text class="row__label">{{ s.label }}</text>
          <view class="switch" :class="{ on: s.value }" @click="toggle(s)">
            <view class="switch__dot" />
          </view>
        </view>
      </PkCard>

      <PkCard class="col">
        <view v-for="i in items" :key="i.key" class="row" @click="i.action && i.action()">
          <text class="row__label">{{ i.label }}</text>
          <view class="row__right">
            <text class="row__value">{{ i.value }}</text>
            <text class="row__arrow">›</text>
          </view>
        </view>
      </PkCard>
    </view>

    <view class="footer">
      <PkButton type="red" text="退出登录" @click="logout" />
      <PkButton type="gold" text="恢复默认设置" @click="reset" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()

const DEFAULT_SWITCHES = [
  { key: 'sound',   label: '音效',     value: true },
  { key: 'bgm',     label: '背景音乐', value: true },
  { key: 'vibrate', label: '震动',     value: false },
  { key: 'auto',    label: '自动挂机', value: false },
]

function loadSwitches() {
  const saved = uni.getStorageSync('settings')
  return DEFAULT_SWITCHES.map(s => ({
    ...s,
    value: saved && saved[s.key] != null ? !!saved[s.key] : s.value,
  }))
}

const switches = ref(loadSwitches())
const items = ref([
  { key: 'lang', label: '语言', value: '简体中文', action: null },
  { key: 'cache', label: '清除缓存', value: '128MB', action: () => uni.showToast({ title: '缓存已清除', icon: 'success' }) },
  { key: 'update', label: '检查更新', value: '已是最新', action: null },
  { key: 'about', label: '关于我们', value: '', action: null },
  { key: 'version', label: '版本号', value: 'V1.0.0', action: null },
])

function persist() {
  const data = {}
  switches.value.forEach(s => { data[s.key] = s.value })
  uni.setStorageSync('settings', data)
}
function toggle(s) {
  s.value = !s.value
  persist()
}
function back() { uni.navigateBack() }
function logout() {
  uni.showModal({
    title: '提示',
    content: '确定退出？',
    success: async r => {
      if (!r.confirm) return
      try {
        await userStore.logout()
      } finally {
        uni.reLaunch({ url: '/pages/login/index' })
      }
    },
  })
}
function reset() {
  switches.value = DEFAULT_SWITCHES.map(s => ({ ...s }))
  persist()
  uni.showToast({ title: '已恢复默认', icon: 'success' })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; display: flex; flex-direction: column; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.cols { display: flex; gap: 24rpx; }
.col { flex: 1; padding: 16rpx 32rpx; }
.row { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 0; border-bottom: 1rpx solid $color-border-light; }
.row:last-child { border-bottom: none; }
.row__label { color: #fff; font-size: $font-size-md; }
.row__right { display: flex; align-items: center; gap: 12rpx; }
.row__value { color: $color-text-secondary; font-size: $font-size-sm; }
.row__arrow { color: $color-text-secondary; font-size: 36rpx; }
.switch {
  width: 80rpx; height: 44rpx;
  background: $color-text-placeholder;
  border-radius: $radius-full;
  position: relative;
  transition: background 0.2s;
}
.switch.on { background: $color-success; }
.switch__dot {
  position: absolute; top: 4rpx; left: 4rpx;
  width: 36rpx; height: 36rpx;
  background: #fff; border-radius: 50%;
  transition: left 0.2s;
}
.switch.on .switch__dot { left: 40rpx; }
.footer { display: flex; gap: 20rpx; justify-content: center; margin-top: 32rpx; }
</style>
