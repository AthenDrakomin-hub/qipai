<template>
  <view class="table">
    <!-- 顶部信息条 -->
    <view class="table__top">
      <text class="table__room">房间 {{ roomNo }}</text>
      <text class="table__round">第 {{ currentRound }}/{{ totalRound }} 局</text>
      <text class="table__blind">盲注 {{ blind }}</text>
    </view>

    <!-- 牌桌 -->
    <view class="table__felt">
      <!-- 底池 -->
      <view class="table__pot">
        <text class="table__pot-label">底池</text>
        <text class="table__pot-value">{{ formatNumber(pot) }}</text>
      </view>

      <!-- 公共牌 -->
      <view class="table__board">
        <PokerCard
          v-for="(c, i) in communityCards"
          :key="i"
          :suit="c.suit"
          :value="c.value"
          face-up
          size="md"
        />
      </view>

      <!-- 其他玩家座位 -->
      <view
        v-for="(p, i) in opponents"
        :key="p.id"
        class="table__seat"
        :class="`table__seat--${p.position}`"
      >
        <view class="table__player" :class="{ active: p.isTurn }">
          <view class="table__avatar-wrap">
            <PkAvatar :src="p.avatar" :name="p.name" :size="72" />
            <view v-if="p.isTurn" class="table__countdown">{{ p.countdown }}</view>
          </view>
          <text class="table__player-name">{{ p.name }}</text>
          <text class="table__player-chip">{{ formatNumber(p.chip) }}</text>
          <view v-if="p.action" class="table__player-action">{{ p.action }}</view>
        </view>
      </view>

      <!-- 自己座位 -->
      <view class="table__seat table__seat--self">
        <view class="table__player table__player--self">
          <view class="table__avatar-wrap">
            <PkAvatar :src="self.avatar" :name="self.name" :size="80" />
            <view v-if="self.isDealer" class="table__dealer">D</view>
          </view>
          <text class="table__player-name">{{ self.name }}</text>
          <text class="table__player-chip">{{ formatNumber(self.chip) }}</text>
          <view class="table__self-cards">
            <PokerCard
              v-for="(c, i) in self.hand"
              :key="i"
              :suit="c.suit"
              :value="c.value"
              face-up
              size="md"
            />
          </view>
        </view>
      </view>
    </view>

    <!-- 操作区 -->
    <view class="table__actions">
      <view class="table__quicks">
        <PkButton size="sm" type="gray" text="1/2 底池" @click="raiseHalf" />
        <PkButton size="sm" type="gray" text="2/3 底池" @click="raiseTwoThirds" />
        <PkButton size="sm" type="gray" text="1× 底池" @click="raisePot" />
      </view>
      <PkButton size="lg" type="red"    text="弃牌" @click="fold" />
      <PkButton size="lg" type="green"  :text="`跟注 ${callAmount}`" @click="call" />
      <PkButton size="lg" type="gold"   text="加注" @click="raise" />
    </view>

    <!-- 底部功能条 -->
    <view class="table__toolbar">
      <view class="table__tool" @click="onChat"><text>💬</text></view>
      <view class="table__tool" @click="onEmoji"><text>😀</text></view>
      <view class="table__tool" @click="onSetting"><text>⚙️</text></view>
      <view class="table__tool" @click="onExit"><text>🚪</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PokerCard from '@/components/PokerCard/index.vue'
import PkAvatar from '@/components/PkAvatar/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { api } from '@/api/index.js'
import { formatNumber } from '@/utils/format.js'

const MY_ID = 10086

const roomNo = ref('880123')
const roomId = ref(101)
const currentRound = ref(3)
const totalRound = ref(25)
const blind = ref('100/200')
const pot = ref(8600)
const callAmount = ref(200)
const acting = ref(false)

onLoad((options) => {
  if (options?.roomNo) roomNo.value = options.roomNo
  if (options?.roomId) roomId.value = options.roomId
})

const communityCards = ref([
  { suit: 'heart',   value: 'A' },
  { suit: 'spade',   value: '3' },
  { suit: 'heart',   value: '10' },
  { suit: 'diamond', value: 'K' },
  { suit: 'club',    value: 'Q' },
])

const self = ref({
  name: '我',
  chip: 30000,
  avatar: '',
  isDealer: false,
  hand: [
    { suit: 'heart', value: '7' },
    { suit: 'diamond', value: '9' },
  ],
})

const opponents = ref([
  { id: 1, name: '老张', chip: 19000, position: 'top-left',    isTurn: false, countdown: 0,   action: '弃牌', avatar: '' },
  { id: 2, name: '小王', chip: 32000, position: 'top',        isTurn: true,  countdown: 15,  action: '加注', avatar: '' },
  { id: 3, name: '莉莉', chip: 8400,  position: 'top-right',  isTurn: false, countdown: 0,   action: '加注', avatar: '' },
  { id: 4, name: '阿强', chip: 6300,  position: 'bottom-left', isTurn: false, countdown: 0,   action: '弃牌', avatar: '' },
])

// 操作统一走服务端回合接口（mock 模式返回模拟牌局数据），保证多端状态一致
async function act(label) {
  if (acting.value) return
  acting.value = true
  try {
    const round = await api.playRound(roomId.value)
    if (round && round.round) currentRound.value = round.round
    const hole = round && round.cards && round.cards.holeCards && round.cards.holeCards[MY_ID]
    if (hole) self.value.hand = hole
    uni.showToast({ title: label, icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    acting.value = false
  }
}

function fold() { act('已弃牌') }
function call() { act(`跟注 ${callAmount.value}`) }
function raise() { act('加注') }
function raiseHalf()  { act('加注 1/2 底池') }
function raiseTwoThirds() { act('加注 2/3 底池') }
function raisePot()   { act('加注 1× 底池') }

function onChat()    { uni.showToast({ title: '聊天（待对接）', icon: 'none' }) }
function onEmoji()    { uni.showToast({ title: '表情（待对接）', icon: 'none' }) }
function onSetting() { uni.showToast({ title: '设置（待对接）', icon: 'none' }) }
function onExit() {
  uni.showModal({
    title: '提示',
    content: '确定要退出房间吗？',
    success: (r) => r.confirm && uni.navigateBack(),
  })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.table {
  @include page-bg;
  width: 100%; min-height: 100vh;
  display: flex; flex-direction: column;
  position: relative;
  overflow: hidden;

  &__top {
    display: flex; justify-content: space-between;
    padding: 16rpx 40rpx;
    color: #fff; font-size: $font-size-sm;
  }
  &__room, &__round, &__blind { color: #fff; }

  &__felt {
    flex: 1;
    position: relative;
    margin: 0 40rpx;
    background: radial-gradient(ellipse at center, #1b7a5a 0%, #0e5c42 70%, #0a4532 100%);
    border-radius: 50% / 45%;
    border: 8rpx solid #3d2817;
    box-shadow: inset 0 0 80rpx rgba(0,0,0,0.4), 0 8rpx 32rpx rgba(0,0,0,0.5);
  }

  &__pot {
    position: absolute;
    top: 8%; left: 50%; transform: translateX(-50%);
    text-align: center;
  }
  &__pot-label { display: block; color: rgba(255,255,255,0.7); font-size: $font-size-sm; }
  &__pot-value {
    display: block; color: $color-text-gold;
    font-size: $font-size-xxl; font-weight: bold;
    font-family: $font-family-numeric;
  }

  &__board {
    position: absolute;
    top: 22%; left: 50%; transform: translateX(-50%);
    display: flex; gap: 12rpx;
  }

  &__seat { position: absolute; }
  &__seat--top-left    { top: 5%;  left: 5%; }
  &__seat--top         { top: 2%;  left: 50%; transform: translateX(-50%); }
  &__seat--top-right   { top: 5%;  right: 5%; }
  &__seat--bottom-left { bottom: 5%; left: 8%; }
  &__seat--self        { bottom: 2%; left: 50%; transform: translateX(-50%); }

  &__player {
    display: flex; flex-direction: column; align-items: center; gap: 4rpx;
    padding: 12rpx 20rpx;
    background: rgba(0,0,0,0.5);
    border: 2rpx solid transparent;
    border-radius: $radius-lg;

    &.active {
      border-color: $color-brand-gold;
      box-shadow: $shadow-gold-glow;
    }
    &--self {
      border-color: #3ed683;
      background: rgba(0, 40, 20, 0.6);
    }
  }

  &__avatar-wrap { position: relative; }
  &__countdown {
    position: absolute; inset: -8rpx;
    border: 4rpx solid $color-brand-gold;
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    color: $color-text-gold; font-size: 20rpx; font-weight: bold;
  }
  &__dealer {
    position: absolute; bottom: -8rpx; right: -8rpx;
    width: 32rpx; height: 32rpx;
    background: #fff; color: #000;
    border-radius: 50%;
    font-size: 18rpx; font-weight: bold;
    display: flex; align-items: center; justify-content: center;
  }
  &__player-name { color: #fff; font-size: $font-size-xs; }
  &__player-chip  { color: $color-text-gold; font-size: $font-size-sm; font-weight: bold; }
  &__player-action {
    color: #ff6b6b; font-size: 20rpx;
  }

  &__self-cards { display: flex; gap: 8rpx; margin-top: 8rpx; }

  &__actions {
    display: flex; align-items: center; justify-content: center;
    gap: 20rpx;
    padding: 20rpx 40rpx;
  }
  &__quicks { display: flex; gap: 12rpx; }

  &__toolbar {
    display: flex; justify-content: center; gap: 40rpx;
    padding: 12rpx 0 24rpx;
  }
  &__tool {
    width: 72rpx; height: 72rpx;
    background: rgba(0,0,0,0.4);
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 32rpx;
  }
}
</style>
