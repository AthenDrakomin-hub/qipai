<template>
  <view class="page">
    <view class="page__back" @click="back">←</view>
    <view class="err">
      <view class="err__icon">⚠️</view>
      <text class="err__title">房间不存在</text>
      <text class="err__desc">您输入的房间号不存在，请检查后重试</text>
      <view class="err__btns">
        <PkButton type="gray" text="返回大厅" @click="home" />
        <PkButton type="gold" text="重试" @click="retry" />
      </view>
    </view>
  </view>
</template>

<script setup>
import PkButton from '@/components/PkButton/index.vue'
function back() { uni.navigateBack() }
function home() { uni.reLaunch({ url: '/pages/hall/index' }) }
function retry() { uni.navigateBack() }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; width: 100%; min-height: 100vh; position: relative; display: flex; align-items: center; justify-content: center; }
.page__back { position: absolute; top: 32rpx; left: 40rpx; color: #fff; font-size: 40rpx; }
.err { text-align: center; }
.err__icon { font-size: 120rpx; margin-bottom: 32rpx; }
.err__title { display: block; color: #fff; font-size: $font-size-xl; font-weight: bold; }
.err__desc { display: block; color: $color-text-secondary; font-size: $font-size-base; margin-top: 16rpx; }
.err__btns { display: flex; gap: 20rpx; justify-content: center; margin-top: 48rpx; }
</style>
