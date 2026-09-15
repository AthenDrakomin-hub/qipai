<template>
  <view class="splash">
    <view class="splash__lanterns">
      <view v-for="i in 6" :key="i" class="splash__lantern" :style="{ left: (i * 16 - 5) + '%' }" />
    </view>

    <view class="splash__logo">
      <view class="splash__vmark">V</view>
      <text class="splash__brand">V-POKER</text>
      <text class="splash__sub">龙腾竞技平台</text>
    </view>

    <view class="splash__progress">
      <view class="splash__progress-bar" :style="{ width: progress + '%' }" />
    </view>
    <text class="splash__loading-text">正在加载资源…</text>

    <view class="splash__footer">
      <text class="splash__version">版本 1.0.0</text>
      <text class="splash__copy">抵制不良游戏 拒绝盗版游戏 适度游戏益脑 沉迷游戏伤身</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/api/index.js'

const progress = ref(0)

onMounted(async () => {
  // 1) 健康检查（mock 走 api.health）
  try { await api.health() } catch (e) { /* mock 不会失败 */ }

  // 2) 进度条动画
  const timer = setInterval(() => {
    progress.value += Math.random() * 12
    if (progress.value >= 100) {
      progress.value = 100
      clearInterval(timer)
      // 3) 根据 token 判断落地页
      const token = uni.getStorageSync('token')
      setTimeout(() => {
        uni.reLaunch({ url: token ? '/pages/hall/index' : '/pages/login/index' })
      }, 400)
    }
  }, 120)
})
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.splash {
  @include page-bg;
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  &__lantern {
    position: absolute;
    top: 0;
    width: 80rpx;
    height: 140rpx;
    background: radial-gradient(circle at 50% 40%, #ff6b5b, #c0392b);
    border-radius: 40px 40px 30px 30px;
    box-shadow: 0 0 40rpx rgba(255, 100, 80, 0.6);
  }

  &__logo {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16rpx;
  }

  &__vmark {
    width: 160rpx;
    height: 160rpx;
    border: 8rpx solid $color-brand-gold;
    border-radius: 32rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $color-brand-gold;
    font-size: 96rpx;
    font-weight: 900;
    font-family: $font-family-logo;
    box-shadow: $shadow-gold-glow;
  }

  &__brand {
    font-size: $font-size-xxl;
    font-weight: 900;
    color: $color-text-gold;
    letter-spacing: 8rpx;
  }

  &__sub {
    font-size: $font-size-base;
    color: $color-text-secondary;
  }

  &__progress {
    margin-top: 64rpx;
    width: 480rpx;
    height: 12rpx;
    background: rgba(255, 255, 255, 0.1);
    border-radius: $radius-full;
    overflow: hidden;
  }

  &__progress-bar {
    height: 100%;
    background: $gradient-btn-gold;
    transition: width 0.2s;
  }

  &__loading-text {
    margin-top: 20rpx;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }

  &__footer {
    position: absolute;
    bottom: 32rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;
  }

  &__version {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }

  &__copy {
    color: $color-text-placeholder;
    font-size: $font-size-xs;
  }
}
</style>
