<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">消息通知</text>
    </view>

    <PkTab v-model="filter" :tabs="tabs" />

    <view class="list">
      <PkCard v-for="m in msgs" :key="m.id" class="item">
        <view class="item__icon">{{ m.icon }}</view>
        <view class="item__main">
          <view class="item__title-row">
            <text class="item__title">{{ m.title }}</text>
            <view v-if="!m.read" class="item__dot" />
          </view>
          <text class="item__desc">{{ m.desc }}</text>
        </view>
        <text class="item__time">{{ m.time }}</text>
      </PkCard>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkTab from '@/components/PkTab/index.vue'

const filter = ref('all')
const tabs = [
  { label: '全部', value: 'all' },
  { label: '系统', value: 'sys' },
  { label: '佣金', value: 'agent' },
  { label: '投诉', value: 'cs' },
]
const msgs = ref([
  { id: 1, icon: '🔔', title: '系统通知', desc: '您的账号已成功登录，如非本人操作请及时修改密码', time: '10分钟前', read: false },
  { id: 2, icon: '💰', title: '充值成功', desc: '您的6元充值已到账，赠送60万金币', time: '2小时前', read: true },
  { id: 3, icon: '💜', title: '佣金到账', desc: '您昨日的代理佣金3,200已发放至账户', time: '昨天 18:00', read: false },
  { id: 4, icon: '🏆', title: '比赛邀请', desc: '您被邀请参加周六锦标赛', time: '昨天 10:00', read: true },
  { id: 5, icon: '🎧', title: '投诉处理', desc: '您提交的投诉已处理完成', time: '3天前', read: true },
])
function back() { uni.navigateBack() }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.list { margin-top: 24rpx; display: flex; flex-direction: column; gap: 12rpx; overflow: auto; }
.item { padding: 24rpx 32rpx; display: flex; align-items: center; gap: 20rpx; }
.item__icon { font-size: 40rpx; }
.item__main { flex: 1; }
.item__title-row { display: flex; align-items: center; gap: 12rpx; }
.item__title { color: #fff; font-size: $font-size-md; font-weight: bold; }
.item__dot { width: 16rpx; height: 16rpx; background: $color-error; border-radius: 50%; }
.item__desc { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-top: 8rpx; }
.item__time { color: $color-text-placeholder; font-size: $font-size-xs; }
</style>
