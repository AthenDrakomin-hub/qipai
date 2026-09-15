<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">代理中心</text>
    </view>

    <PkCard class="agent-card">
      <view class="agent-row">
        <text class="crown">👑</text>
        <view>
          <text class="label">代理等级</text>
          <text class="value">VIP3</text>
        </view>
        <view>
          <text class="label">佣金比例</text>
          <text class="value">15%</text>
        </view>
      </view>
      <text class="label">预计今日佣金</text>
      <text class="big">3,280</text>
    </PkCard>

    <view class="entries">
      <view v-for="e in entries" :key="e.key" class="entry" @click="go(e.key)">
        <text class="entry__icon">{{ e.icon }}</text>
        <text class="entry__name">{{ e.name }}</text>
      </view>
    </view>

    <text class="tip">完整代理功能请登录代理后台</text>
    <PkButton type="gold" text="前往代理后台" @click="goAdmin" />
  </view>
</template>

<script setup>
import PkCard from '@/components/PkCard/index.vue'
import PkButton from '@/components/PkButton/index.vue'

const entries = [
  { key: 'member',  name: '会员管理', icon: '👥' },
  { key: 'commission', name: '佣金记录', icon: '💰' },
  { key: 'data',     name: '数据看板', icon: '📊' },
  { key: 'link',     name: '推广链接', icon: '🔗' },
]
function back() { uni.navigateBack() }
function go(k) { uni.showToast({ title: `${k}（待对接）`, icon: 'none' }) }
function goAdmin() { uni.showToast({ title: '跳转代理后台（待对接）', icon: 'none' }) }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; display: flex; flex-direction: column; align-items: center; gap: 32rpx; }
.page__header { display: flex; align-items: center; width: 100%; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.agent-card { width: 80%; padding: 40rpx; text-align: center; }
.agent-row { display: flex; justify-content: space-around; align-items: center; margin-bottom: 24rpx; }
.crown { font-size: 80rpx; }
.label { display: block; color: $color-text-secondary; font-size: $font-size-sm; }
.value { display: block; color: #fff; font-size: $font-size-xl; font-weight: bold; }
.big { display: block; color: $color-text-gold; font-size: $font-size-xxl; font-weight: bold; }
.entries { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24rpx; width: 80%; }
.entry {
  padding: 40rpx; background: $color-bg-panel;
  border: 2rpx solid $color-border-gold; border-radius: $radius-lg;
  display: flex; flex-direction: column; align-items: center; gap: 12rpx;
}
.entry__icon { font-size: 56rpx; }
.entry__name { color: #fff; font-size: $font-size-md; }
.tip { color: $color-text-secondary; font-size: $font-size-sm; }
</style>
