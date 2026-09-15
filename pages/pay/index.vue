<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">游戏充值</text>
      <text class="page__balance">余额 {{ formatNumber(userStore.credits) }}</text>
    </view>

    <view class="grid">
      <PkCard v-for="p in packages" :key="p.id" :active="p.id === selected" @click="selected = p.id" class="pkg">
        <text class="pkg__rmb">{{ p.rmb }}元</text>
        <text class="pkg__coin">{{ p.coin }}金币</text>
        <view class="pkg__coins">🪙</view>
      </PkCard>
    </view>

    <view class="section">
      <text class="section__label">自定义金额</text>
      <PkInput v-model="custom" type="number" placeholder="请输入充值金额">
        <template #suffix><text>元</text></template>
      </PkInput>
    </view>

    <view class="section">
      <text class="section__label">选择支付方式</text>
      <view class="pays">
        <view v-for="m in pays" :key="m.id" class="pay" :class="{ active: payId === m.id }" @click="payId = m.id">
          <text>{{ m.icon }}</text><text>{{ m.name }}</text>
        </view>
      </view>
    </view>

    <PkButton type="gold" size="lg" block text="立即充值" @click="submit" />
    <text class="hint">充值即代表同意充值协议</text>
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
const selected = ref(2)
const custom = ref('')
const payId = ref('alipay')
const packages = ref([
  { id: 1, rmb: 6,   coin: '60万' },
  { id: 2, rmb: 30,  coin: '300万' },
  { id: 3, rmb: 68,  coin: '680万' },
  { id: 4, rmb: 128, coin: '1280万' },
  { id: 5, rmb: 328, coin: '3280万' },
  { id: 6, rmb: 648, coin: '6480万' },
])
const pays = [
  { id: 'alipay', name: '支付宝', icon: '💙' },
  { id: 'wx',     name: '微信',   icon: '💚' },
  { id: 'bank',   name: '银行卡', icon: '💳' },
]
function back() { uni.navigateBack() }
function submit() {
  const pkg = packages.value.find(p => p.id === selected.value)
  const yuan = Number(custom.value) || (pkg ? pkg.rmb : 0)
  if (yuan <= 0) {
    uni.showToast({ title: '请选择或输入充值金额', icon: 'none' })
    return
  }
  uni.showToast({ title: '充值通道对接中，敬请期待', icon: 'none' })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; display: flex; flex-direction: column; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.page__balance { color: $color-text-secondary; font-size: $font-size-sm; }
.grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 20rpx; }
.pkg { padding: 24rpx; text-align: center; }
.pkg__rmb { display: block; color: #fff; font-size: $font-size-lg; font-weight: bold; }
.pkg__coin { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-top: 8rpx; }
.pkg__coins { font-size: 48rpx; margin-top: 16rpx; }
.section { margin-top: 32rpx; }
.section__label { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-bottom: 12rpx; }
.pays { display: flex; gap: 20rpx; }
.pay { padding: 16rpx 32rpx; background: $color-bg-card; border-radius: $radius-base; color: #fff; border: 2rpx solid transparent; display: flex; gap: 8rpx; align-items: center; }
.pay.active { border-color: $color-border-gold-strong; background: rgba(230,194,90,0.15); }
.hint { text-align: center; color: $color-text-placeholder; font-size: $font-size-xs; margin-top: 16rpx; }
</style>
