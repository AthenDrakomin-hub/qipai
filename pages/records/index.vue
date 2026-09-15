<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">战绩记录</text>
    </view>

    <PkTab v-model="filter" :tabs="tabs" />

    <view class="list">
      <PkCard v-for="r in filteredRecords" :key="r.id" class="item">
        <view class="item__main">
          <text class="item__icon">{{ r.icon }}</text>
          <view class="item__info">
            <text class="item__name">{{ r.game }} · {{ r.roomNo }}</text>
            <text class="item__meta">{{ r.round }}局 · 流水 {{ r.flow }} · {{ r.time }}</text>
          </view>
        </view>
        <text class="item__amount" :class="r.profit >= 0 ? 'win' : 'lose'">
          {{ formatAmount(r.profit, { sign: true }) }}
        </text>
      </PkCard>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkTab from '@/components/PkTab/index.vue'
import { api } from '@/api/index.js'
import { formatAmount } from '@/utils/format.js'

const filter = ref('all')
const tabs = [
  { label: '全部', value: 'all' },
  { label: '德州', value: 'TEXAS' },
  { label: '金花', value: 'JINHUA' },
  { label: '三公', value: 'SANGONG' },
  { label: '牛牛', value: 'DOUNIU' },
]

const GAME_NAME = {
  TEXAS: '德州', JINHUA: '金花', SANGONG: '三公',
  DOUNIU: '牛牛', TONGBI_NIUNIU: '通比牛牛', TONGBI_SANGONG: '通比三公',
}
const GAME_ICON = {
  TEXAS: '♠️', JINHUA: '🃏', SANGONG: '🎴',
  DOUNIU: '🐂', TONGBI_NIUNIU: '🐃', TONGBI_SANGONG: '🎲',
}

const records = ref([])
const filteredRecords = computed(() =>
  records.value.filter(r => filter.value === 'all' || r.gameType === filter.value)
)

onMounted(async () => {
  try {
    const page = await api.gameHistory(1, 20)
    records.value = page.records.map(r => ({
      id: r.id,
      gameType: r.gameType || 'TEXAS',
      game: GAME_NAME[r.gameType] || '对局',
      roomNo: r.roomNo,
      round: r.roundNo,
      flow: formatAmount(r.turnover),
      time: (r.createTime || '').slice(5, 16),
      profit: Number(r.profit) || 0,
      icon: GAME_ICON[r.gameType] || '♠️',
    }))
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
})
function back() { uni.navigateBack() }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.list { margin-top: 24rpx; display: flex; flex-direction: column; gap: 16rpx; overflow: auto; }
.item { padding: 24rpx 32rpx; display: flex; align-items: center; justify-content: space-between; }
.item__main { display: flex; align-items: center; gap: 20rpx; }
.item__icon { font-size: 48rpx; }
.item__name { display: block; color: #fff; font-size: $font-size-md; font-weight: bold; }
.item__meta { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-top: 4rpx; }
.item__amount { font-size: $font-size-lg; font-weight: bold; font-family: $font-family-numeric; }
.item__amount.win  { color: $color-text-green; }
.item__amount.lose { color: $color-text-red; }
</style>
