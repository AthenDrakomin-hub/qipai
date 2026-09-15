<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">赠送游戏币</text>
      <text class="page__balance">我的余额 {{ formatNumber(userStore.credits) }}</text>
    </view>

    <view class="cols">
      <PkCard class="col">
        <text class="label">选择玩家</text>
        <view v-for="p in players" :key="p.id" class="player" :class="{ active: selected === p.id }" @click="selected = p.id">
          <PkAvatar :name="p.name" :size="64" />
          <view class="player__main">
            <text class="player__name">{{ p.name }}</text>
            <text class="player__id">ID: {{ p.id }}</text>
          </view>
          <text class="player__chip">{{ p.chip }}</text>
        </view>
      </PkCard>

      <PkCard class="col">
        <text class="label">赠送金额</text>
        <PkInput v-model="amount" type="number" placeholder="请输入赠送金额" />
        <view class="quicks">
          <view v-for="a in quicks" :key="a" class="quick" @click="amount = String(a)">{{ a }}</view>
        </view>
        <text class="label" style="margin-top: 24rpx">备注（选填）</text>
        <PkInput v-model="note" placeholder="请输入备注信息" />
        <PkButton type="gold" size="lg" block :loading="submitting" text="确认赠送" @click="submit" style="margin-top: 32rpx" />
        <text class="warn" v-if="error">{{ error }}</text>
      </PkCard>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkInput from '@/components/PkInput/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import PkAvatar from '@/components/PkAvatar/index.vue'
import { api } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { formatNumber } from '@/utils/format.js'

const userStore = useUserStore()
const selected = ref('')
const amount = ref('')
const note = ref('')
const error = ref('')
const submitting = ref(false)
const players = ref([])
const quicks = [1000, 5000, 10000, 50000]

onMounted(async () => {
  try {
    const list = await api.subordinates()
    players.value = list.map(s => ({ id: String(s.id), name: s.nickname, chip: formatNumber(s.credits) }))
    if (players.value.length) selected.value = players.value[0].id
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
})

function back() { uni.navigateBack() }
async function submit() {
  if (!selected.value) { error.value = '请选择玩家'; return }
  const value = Number(amount.value)
  if (!value || value <= 0) { error.value = '请输入赠送金额'; return }
  if (value > userStore.credits) { error.value = '余额不足'; return }
  error.value = ''
  submitting.value = true
  try {
    const left = await api.gift({ targetUserId: selected.value, changeValue: value, remark: note.value })
    userStore.setCredits(left)
    uni.showToast({ title: '赠送成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.page__balance { color: $color-text-secondary; font-size: $font-size-sm; }
.cols { display: flex; gap: 24rpx; }
.col { flex: 1; padding: 32rpx; }
.label { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-bottom: 12rpx; }
.player {
  display: flex; align-items: center; gap: 16rpx;
  padding: 16rpx; border-radius: $radius-base;
  border: 2rpx solid transparent; margin-bottom: 12rpx;
}
.player.active { border-color: $color-border-gold-strong; background: rgba(230,194,90,0.1); }
.player__main { flex: 1; }
.player__name { display: block; color: #fff; }
.player__id { display: block; color: $color-text-secondary; font-size: $font-size-xs; }
.player__chip { color: $color-text-gold; font-weight: bold; }
.quicks { display: flex; gap: 12rpx; margin: 16rpx 0; }
.quick { padding: 10rpx 24rpx; background: $color-bg-card; border-radius: $radius-base; color: #fff; }
.warn { display: block; text-align: center; color: $color-error; font-size: $font-size-xs; margin-top: 12rpx; }
</style>
