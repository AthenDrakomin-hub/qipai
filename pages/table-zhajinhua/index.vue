<template>
  <view class="table">
    <view class="table__top">
      <text>房间 {{ roomNo }}</text><text>第{{ currentRound }}局/共{{ totalRound }}局</text><text>盲注 {{ blind }}</text>
    </view>
    <view class="felt">
      <view class="pot"><text>底池</text><text class="num">{{ formatNumber(pot) }}</text></view>
      <view v-for="p in opponents" :key="p.id" class="seat" :class="p.pos">
        <view class="player" :class="{ turn: p.turn }">
          <PkAvatar :name="p.name" :size="64" />
          <text class="name">{{ p.name }}</text>
          <text class="chip">{{ p.chip }}</text>
          <text class="action">{{ p.action }}</text>
        </view>
      </view>
      <view class="seat self">
        <view class="player self">
          <PkAvatar name="我" :size="72" />
          <text class="name">我</text>
          <text class="chip">{{ formatNumber(selfChip) }}</text>
          <view class="cards">
            <PokerCard v-for="(c,i) in hand" :key="i" :suit="c.suit" :value="c.value" face-up size="md" />
          </view>
        </view>
      </view>
    </view>
    <view class="actions">
      <PkButton size="sm" type="gray" text="1/2底池" @click="act('加注 1/2底池')" />
      <PkButton size="sm" type="gray" text="1×底池" @click="act('加注 1×底池')" />
      <PkButton size="lg" type="red" text="弃牌" @click="act('已弃牌')" />
      <PkButton size="lg" type="blue" text="看牌" @click="act('看牌')" />
      <PkButton size="lg" type="green" text="跟注100" @click="act('跟注 100')" />
      <PkButton size="lg" type="purple" text="加注" @click="act('加注')" />
      <PkButton size="lg" type="gold" text="比牌" @click="act('比牌')" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PkAvatar from '@/components/PkAvatar/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import PokerCard from '@/components/PokerCard/index.vue'
import { api } from '@/api/index.js'
import { formatNumber } from '@/utils/format.js'

const roomNo = ref('660123')
const roomId = ref(201)
const currentRound = ref(5)
const totalRound = ref(25)
const blind = ref('50/100')
const pot = ref(12500)
const selfChip = ref(30000)
const acting = ref(false)

onLoad((options) => {
  if (options?.roomNo) roomNo.value = options.roomNo
  if (options?.roomId) roomId.value = options.roomId
})

const opponents = ref([
  { id: 1, name: '老张', chip: '1.9万', pos: 'tl', turn: false, action: '闷牌' },
  { id: 2, name: '小王', chip: '3.2万', pos: 't',  turn: true,  action: '看牌' },
  { id: 3, name: '莉莉', chip: '8.4千', pos: 'tr', turn: false, action: '看牌' },
  { id: 4, name: '阿强', chip: '6.3千', pos: 'bl', turn: false, action: '闷牌' },
])
const hand = ref([
  { suit: 'heart', value: 'Q' },
  { suit: 'diamond', value: 'J' },
  { suit: 'club', value: '10' },
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
.table {
  background-image: url("/static/generated/table-zhajinhua-background.png");
  background-size: cover;
  background-position: center; @include page-bg; min-height: 100vh; display: flex; flex-direction: column; }
.table__top { display: flex; justify-content: space-between; padding: 16rpx 40rpx; color: #fff; font-size: $font-size-sm; }
.felt {
  flex: 1; position: relative; margin: 0 40rpx;
  background: radial-gradient(ellipse at center, #1E3A8A 0%, #142554 70%, #0B1A3A 100%);
  border-radius: 50% / 45%; border: 8rpx solid #3d2817;
}
.pot { position: absolute; top: 8%; left: 50%; transform: translateX(-50%); text-align: center; color: rgba(255,255,255,0.7); }
.pot .num { display: block; color: $color-text-gold; font-size: $font-size-xxl; font-weight: bold; }
.seat { position: absolute; }
.seat.tl { top: 8%; left: 6%; } .seat.t { top: 4%; left: 50%; transform: translateX(-50%); }
.seat.tr { top: 8%; right: 6%; } .seat.bl { bottom: 8%; left: 10%; }
.seat.self { bottom: 2%; left: 50%; transform: translateX(-50%); }
.player { display: flex; flex-direction: column; align-items: center; padding: 12rpx 20rpx; background: rgba(0,0,0,0.5); border-radius: $radius-lg; border: 2rpx solid transparent; }
.player.turn { border-color: $color-brand-gold; }
.player.self { border-color: #3ed683; }
.name { color: #fff; font-size: $font-size-xs; }
.chip { color: $color-text-gold; font-size: $font-size-sm; font-weight: bold; }
.action { color: #ff6b6b; font-size: 20rpx; }
.cards { display: flex; gap: 8rpx; margin-top: 8rpx; }
.actions { display: flex; justify-content: center; gap: 16rpx; padding: 20rpx; }
</style>
