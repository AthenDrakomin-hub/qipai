<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">邀请好友</text>
    </view>

    <view class="cols">
      <PkCard class="col">
        <text class="label">我的邀请码</text>
        <view class="code-row">
          <text class="code">VPK88888</text>
          <PkButton size="sm" type="blue" text="复制" @click="copy('VPK88888')" />
        </view>
        <text class="label">邀请链接</text>
        <view class="code-row">
          <text class="code small">v-poker.com/r/VPK88888</text>
          <PkButton size="sm" type="blue" text="复制" @click="copy('https://v-poker.com/r/VPK88888')" />
        </view>
        <PkButton type="gold" text="分享给好友" style="margin-top: 24rpx" />
      </PkCard>

      <PkCard class="col" title="已邀请好友 (12人)">
        <view v-for="r in records" :key="r.id" class="row">
          <PkAvatar :name="r.name" :size="56" />
          <view class="row__main">
            <text class="row__name">{{ r.name }}</text>
            <text class="row__time">{{ r.time }}</text>
          </view>
          <text class="row__status" :class="r.paid ? 'win' : ''">{{ r.paid ? '奖励已发放' : '待充值' }}</text>
        </view>
      </PkCard>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import PkAvatar from '@/components/PkAvatar/index.vue'

const records = ref([
  { id: 1, name: '玩家A', time: '09-10 已注册', paid: true },
  { id: 2, name: '玩家B', time: '09-09 已注册', paid: false },
  { id: 3, name: '玩家C', time: '09-08 已注册', paid: true },
  { id: 4, name: '玩家D', time: '09-07 已注册', paid: false },
])
function back() { uni.navigateBack() }
function copy(text) {
  uni.setClipboardData({ data: text, success: () => uni.showToast({ title: '已复制', icon: 'success' }) })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.cols { display: flex; gap: 24rpx; }
.col { flex: 1; padding: 32rpx; }
.label { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-bottom: 12rpx; }
.code-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 20rpx; }
.code { color: $color-text-gold; font-size: $font-size-xl; font-weight: bold; letter-spacing: 4rpx; }
.code.small { font-size: $font-size-sm; }
.row { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 0; border-bottom: 1rpx solid $color-border-light; }
.row__main { flex: 1; }
.row__name { display: block; color: #fff; }
.row__time { display: block; color: $color-text-secondary; font-size: $font-size-xs; }
.row__status { color: $color-text-secondary; font-size: $font-size-sm; }
.row__status.win { color: $color-text-green; }
</style>
