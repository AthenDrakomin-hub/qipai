<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">创建房间</text>
      <text class="page__balance">余额 {{ formatNumber(userStore.credits) }}</text>
    </view>

    <view class="cols">
      <PkCard class="col">
        <text class="label">选择游戏</text>
        <view class="games">
          <view
            v-for="g in games"
            :key="g.id"
            class="game"
            :class="{ active: form.game === g.id }"
            @click="form.game = g.id"
          >
            <text class="game__icon">{{ g.icon }}</text>
            <text class="game__name">{{ g.name }}</text>
          </view>
        </view>
        <text class="label" style="margin-top: 24rpx">房间类型</text>
        <PkTab v-model="form.level" :tabs="levelTabs" />
      </PkCard>

      <PkCard class="col">
        <text class="label">初始筹码</text>
        <view class="quicks">
          <view v-for="a in quicks" :key="a" class="quick" :class="{ active: form.chip === a }" @click="form.chip = a">{{ a }}</view>
        </view>
        <PkInput v-model="form.chipText" placeholder="输入初始筹码金额" />

        <text class="label" style="margin-top: 24rpx">房间密码（选填）</text>
        <PkInput v-model="form.pwd" placeholder="请设置房间密码" />

        <text class="label" style="margin-top: 24rpx">总局数</text>
        <PkInput v-model="form.rounds" type="number" placeholder="默认 25" />

        <PkButton type="gold" size="lg" block :loading="submitting" text="创建房间" @click="submit" style="margin-top: 32rpx" />
        <text class="warn">创建房间需扣除 500 服务费</text>
      </PkCard>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkTab from '@/components/PkTab/index.vue'
import PkInput from '@/components/PkInput/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { api } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { formatNumber } from '@/utils/format.js'

const userStore = useUserStore()
const submitting = ref(false)

// 本地 id → 后端枚举 code
const GAME_CODE = {
  texas: 'TEXAS', zjh: 'JINHUA', sg: 'SANGONG',
  nn: 'DOUNIU', tbnn: 'TONGBI_NIUNIU', tbsg: 'TONGBI_SANGONG',
}
const LEVEL_CODE = { junior: 'PRIMARY', senior: 'ADVANCED', master: 'PREMIUM' }

const form = reactive({ game: 'texas', level: 'junior', chip: 5000, chipText: '', pwd: '', rounds: '25' })
const games = [
  { id: 'texas', name: '德州扑克', icon: '♠️' },
  { id: 'zjh',   name: '炸金花',  icon: '🃏' },
  { id: 'sg',    name: '抢庄三公', icon: '🎴' },
  { id: 'nn',    name: '抢庄牛牛', icon: '🐂' },
  { id: 'tbnn',  name: '通比牛牛', icon: '🐃' },
  { id: 'tbsg',  name: '通比三公', icon: '🎲' },
]
const levelTabs = [
  { label: '初级房', value: 'junior' },
  { label: '高级房', value: 'senior' },
  { label: '顶级房', value: 'master' },
]
const quicks = [1000, 5000, 10000, 50000]
function back() { uni.navigateBack() }
async function submit() {
  const initChip = Number(form.chipText || form.chip)
  if (!initChip || initChip <= 0) {
    uni.showToast({ title: '请输入有效的初始筹码', icon: 'none' })
    return
  }
  if (initChip > userStore.credits) {
    uni.showToast({ title: '余额不足，无法创建房间', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await api.createRoom({
      gameType: GAME_CODE[form.game],
      roomLevel: LEVEL_CODE[form.level],
      initChip,
      password: form.pwd,
      totalRounds: Number(form.rounds) || 25,
    })
    uni.showToast({ title: '房间创建成功', icon: 'success' })
    setTimeout(() => uni.navigateTo({ url: '/pages/my-rooms/index' }), 800)
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    submitting.value = false
  }
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
.label { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-bottom: 12rpx; }
.games { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; margin-bottom: 24rpx; }
.game {
  padding: 20rpx; background: $color-bg-card; border-radius: $radius-base;
  display: flex; flex-direction: column; align-items: center; gap: 8rpx;
  border: 2rpx solid transparent;
}
.game.active { border-color: $color-border-gold-strong; }
.game__icon { font-size: 40rpx; }
.game__name { color: #fff; font-size: $font-size-xs; }
.quicks { display: flex; gap: 12rpx; margin-bottom: 16rpx; }
.quick { padding: 10rpx 24rpx; background: $color-bg-card; border-radius: $radius-base; color: #fff; font-size: $font-size-sm; }
.quick.active { background: $gradient-btn-gold; }
.warn { display: block; text-align: center; color: $color-error; font-size: $font-size-xs; margin-top: 12rpx; }
</style>
