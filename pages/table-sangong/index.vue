<template>
  <view class="table">
    <view class="table__top">
      <text>房间 {{ roomNo }}</text><text>第{{ currentRound }}局/共{{ totalRound }}局</text>
    </view>
    <view class="felt">
      <view class="pot"><text>奖池</text><text class="num">{{ formatAmount(pot) }}</text></view>
      <view class="shangzhuang" @click="act('申请上庄')">我要上庄</view>
      <view v-for="p in players" :key="p.id" class="seat" :class="p.pos">
        <view class="player">
          <PkAvatar :name="p.name" :size="64" />
          <text class="name">{{ p.name }}</text>
          <text class="chip">{{ p.chip }}</text>
        </view>
      </view>
      <view class="bets">
        <view class="bet">200</view><view class="bet">100</view><view class="bet">300</view>
      </view>
    </view>
    <view class="bottom">
      <text class="countdown">下注剩余时间 12</text>
      <view class="chips">
        <PkButton size="sm" type="purple" text="100" @click="act('下注 100')" />
        <PkButton size="sm" type="gold" text="1000" @click="act('下注 1000')" />
        <PkButton size="sm" type="red" text="10000" @click="act('下注 10000')" />
        <PkButton size="sm" type="gray" text="10万" @click="act('下注 10万')" />
        <PkButton size="sm" type="gray" text="100万" @click="act('下注 100万')" />
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PkAvatar from '@/components/PkAvatar/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { api } from '@/api/index.js'
import { formatAmount } from '@/utils/format.js'

const roomNo = ref('770123')
const roomId = ref(301)
const currentRound = ref(3)
const totalRound = ref(25)
const pot = ref(223451108)
const acting = ref(false)

onLoad((options) => {
  if (options?.roomNo) roomNo.value = options.roomNo
  if (options?.roomId) roomId.value = options.roomId
})

const players = ref([
  { id: 1, name: '么么哒', chip: '88.00亿', pos: 't' },
  { id: 2, name: '老张',   chip: '1.9万',   pos: 'tl' },
  { id: 3, name: '小王',   chip: '3.2万',   pos: 'tr' },
  { id: 4, name: '阿强',   chip: '6.3千',   pos: 'bl' },
  { id: 5, name: '莉莉',   chip: '8.4千',   pos: 'br' },
])

// 操作统一走服务端回合接口（mock 模式返回模拟牌局数据）
async function act(label) {
  if (acting.value) return
  acting.value = true
  try {
    const round = await api.playRound(roomId.value)
    if (round && round.round) currentRound.value = round.round
    uni.showToast({ title: label, icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    acting.value = false
  }
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.table { @include page-bg; min-height: 100vh; display: flex; flex-direction: column; }
.table__top { display: flex; justify-content: space-between; padding: 16rpx 40rpx; color: #fff; font-size: $font-size-sm; }
.felt {
  flex: 1; position: relative; margin: 0 40rpx;
  background: radial-gradient(ellipse at center, #14356E 0%, #0B1F4A 70%, #071838 100%);
  border-radius: 50% / 45%; border: 8rpx solid #3d2817;
}
.pot { position: absolute; top: 6%; left: 50%; transform: translateX(-50%); text-align: center; color: rgba(255,255,255,0.7); }
.pot .num { display: block; color: $color-text-gold; font-size: $font-size-xxl; font-weight: bold; }
.shangzhuang {
  position: absolute; top: 18%; left: 50%; transform: translateX(-50%);
  background: $gradient-btn-purple; color: #fff;
  padding: 8rpx 32rpx; border-radius: $radius-full;
}
.seat { position: absolute; }
.seat.t { top: 4%; left: 50%; transform: translateX(-50%); }
.seat.tl { top: 10%; left: 6%; } .seat.tr { top: 10%; right: 6%; }
.seat.bl { bottom: 10%; left: 10%; } .seat.br { bottom: 10%; right: 10%; }
.player { display: flex; flex-direction: column; align-items: center; padding: 12rpx 20rpx; background: rgba(0,0,0,0.5); border-radius: $radius-lg; }
.name { color: #fff; font-size: $font-size-xs; }
.chip { color: $color-text-gold; font-size: $font-size-sm; font-weight: bold; }
.bets { position: absolute; top: 40%; left: 50%; transform: translateX(-50%); display: flex; gap: 40rpx; }
.bet {
  padding: 16rpx 40rpx; background: rgba(123,104,238,0.3);
  border: 2rpx solid $color-brand-purple; border-radius: $radius-base;
  color: #fff; font-weight: bold;
}
.bottom { padding: 16rpx 40rpx; text-align: center; }
.countdown { color: #fff; font-size: $font-size-md; }
.chips { display: flex; justify-content: center; gap: 16rpx; margin-top: 12rpx; }
</style>
