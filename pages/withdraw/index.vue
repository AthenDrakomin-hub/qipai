<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">游戏提现</text>
      <text class="page__balance">当前余额 {{ formatNumber(userStore.credits) }}</text>
    </view>

    <view class="cols">
      <PkCard class="col">
        <text class="label">可提现余额</text>
        <text class="big">{{ formatNumber(userStore.credits) }}</text>
        <PkInput v-model="amount" type="number" placeholder="请输入提现金额" style="margin-top: 24rpx">
          <template #suffix><text>元</text></template>
        </PkInput>
        <PkButton type="ghost" size="sm" text="全部提现" @click="all" style="margin-top: 16rpx" />
      </PkCard>

      <PkCard class="col">
        <text class="label">选择收款方式</text>
        <view class="pays">
          <view v-for="m in pays" :key="m.id" class="pay" :class="{ active: payId === m.id }" @click="payId = m.id">
            <text>{{ m.icon }} {{ m.name }}</text>
          </view>
        </view>
        <PkInput v-model="account" placeholder="请输入收款账号" style="margin-top: 20rpx" />
      </PkCard>
    </view>

    <PkButton type="gold" size="lg" block text="提交申请" @click="submit" />

    <PkCard class="records" title="提现记录">
      <view v-for="r in records" :key="r.id" class="row">
        <text class="row__time">{{ r.time }}</text>
        <text class="row__amount">-{{ r.amount }}</text>
        <text class="row__status">{{ r.status }}</text>
      </view>
    </PkCard>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkInput from '@/components/PkInput/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { useUserStore } from '@/stores/user.js'
import { formatNumber } from '@/utils/format.js'

const userStore = useUserStore()
const amount = ref('')
const account = ref('')
const payId = ref('alipay')
const pays = [
  { id: 'alipay', name: '支付宝', icon: '💙' },
  { id: 'wx', name: '微信', icon: '💚' },
  { id: 'bank', name: '银行卡', icon: '💳' },
]
const records = ref([
  { id: 1, time: '2026-09-10 14:30', amount: '5,000', status: '已到账' },
  { id: 2, time: '2026-09-05 09:15', amount: '2,000', status: '已到账' },
])
function back() { uni.navigateBack() }
function all() { amount.value = String(userStore.credits) }
function submit() {
  const yuan = Number(amount.value)
  if (!yuan || yuan <= 0) {
    uni.showToast({ title: '请输入提现金额', icon: 'none' })
    return
  }
  if (yuan > userStore.credits) {
    uni.showToast({ title: '可提现余额不足', icon: 'none' })
    return
  }
  if (!account.value) {
    uni.showToast({ title: '请输入收款账号', icon: 'none' })
    return
  }
  uni.showToast({ title: '提现通道对接中，敬请期待', icon: 'none' })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; display: flex; flex-direction: column; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.page__balance { color: $color-text-secondary; font-size: $font-size-sm; }
.cols { display: flex; gap: 24rpx; }
.col { flex: 1; padding: 32rpx; }
.label { display: block; color: $color-text-secondary; font-size: $font-size-sm; }
.big { display: block; color: $color-text-gold; font-size: $font-size-xxl; font-weight: bold; margin: 12rpx 0; }
.pays { display: flex; flex-direction: column; gap: 12rpx; margin-top: 16rpx; }
.pay { padding: 16rpx 24rpx; background: $color-bg-card; border-radius: $radius-base; color: #fff; border: 2rpx solid transparent; }
.pay.active { border-color: $color-border-gold-strong; }
.records { margin-top: 24rpx; }
.row { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid $color-border-light; }
.row__time { color: $color-text-secondary; }
.row__amount { color: $color-text-red; }
.row__status { color: $color-success; }
</style>
