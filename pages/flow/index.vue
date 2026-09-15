<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">资金流水</text>
    </view>

    <PkTab v-model="filter" :tabs="tabs" />

    <view class="list">
      <PkCard v-for="f in filteredFlows" :key="f.id" class="row">
        <view class="row__icon">{{ f.icon }}</view>
        <view class="row__main">
          <text class="row__type">{{ f.type }}</text>
          <text class="row__time">{{ f.time }} · {{ f.note }}</text>
        </view>
        <text class="row__amount" :class="f.amount >= 0 ? 'win' : 'lose'">
          {{ formatAmount(f.amount, { sign: true }) }}
        </text>
        <text class="row__balance">余 {{ f.balance }}</text>
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
  { label: '充值', value: 'pay' },
  { label: '提现', value: 'wd' },
  { label: '佣金', value: 'agent' },
  { label: '赠送', value: 'gift' },
  { label: '对局', value: 'game' },
]

// changeType -> { type, icon, tab }
const CT_MAP = {
  1:  ['充值', '💚', 'pay'],  7:  ['充值', '💚', 'pay'],  8:  ['调整', '💚', 'pay'], 13: ['划拨', '💚', 'pay'],
  4:  ['抽水', '💙', 'wd'],  5:  ['水费', '💙', 'wd'],  10: ['补扣', '💙', 'wd'],
  6:  ['分润', '💜', 'agent'], 11: ['返佣', '💜', 'agent'],
  9:  ['代赠', '🎁', 'gift'],  12: ['P2P', '🎁', 'gift'],  14: ['局赠', '🎁', 'gift'],
  2:  ['增减', '♠️', 'game'],  3:  ['对局', '♠️', 'game'],
}

const flows = ref([])
const filteredFlows = computed(() => flows.value.filter(f => filter.value === 'all' || f.tab === filter.value))

onMounted(async () => {
  try {
    const page = await api.creditLog(1, 30)
    flows.value = page.records.map(f => {
      const [type, icon, tab] = CT_MAP[f.changeType] || ['其他', '📄', 'game']
      return {
        id: f.id,
        type, icon, tab,
        time: (f.createTime || '').slice(5, 16),
        note: f.remark || (f.roomNo ? `房间 ${f.roomNo}` : ''),
        amount: f.changeValue,
        balance: formatAmount(f.afterValue),
      }
    })
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
.list { margin-top: 24rpx; display: flex; flex-direction: column; gap: 12rpx; overflow: auto; }
.row { padding: 20rpx 32rpx; display: flex; align-items: center; gap: 20rpx; }
.row__icon { font-size: 40rpx; }
.row__main { flex: 1; }
.row__type { display: block; color: #fff; font-size: $font-size-md; }
.row__time { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-top: 4rpx; }
.row__amount { font-size: $font-size-md; font-weight: bold; font-family: $font-family-numeric; }
.row__amount.win  { color: $color-text-green; }
.row__amount.lose { color: $color-text-red; }
.row__balance { color: $color-text-secondary; font-size: $font-size-sm; min-width: 140rpx; text-align: right; }
</style>
