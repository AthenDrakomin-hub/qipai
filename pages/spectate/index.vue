<template>
  <view class="table">
    <view class="table__top">
      <text class="table__room">房间 {{ roomNo }}</text>
      <text class="table__round">第 {{ round }}/25 局</text>
      <view class="table__tag">旁观中</view>
    </view>

    <view class="table__felt">
      <view class="table__pot">
        <text class="table__pot-label">底池</text>
        <text class="table__pot-value">{{ pot }}</text>
      </view>
      <view class="table__board">
        <PokerCard v-for="(c,i) in board" :key="i" :suit="c.suit" :value="c.value" face-up size="md" />
      </view>
      <view v-for="p in players" :key="p.id" class="table__seat" :class="`table__seat--${p.position}`">
        <view class="table__player">
          <PkAvatar :name="p.name" :size="72" />
          <text class="table__name">{{ p.name }}</text>
          <text class="table__chip">{{ p.chip }}</text>
        </view>
      </view>
    </view>

    <view class="table__actions">
      <PkButton type="gold" text="赠送游戏币" @click="gift" />
      <PkButton type="blue" text="聊天" @click="chat" />
      <PkButton type="gray" text="退出旁观" @click="exit" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PokerCard from '@/components/PokerCard/index.vue'
import PkAvatar from '@/components/PkAvatar/index.vue'
import PkButton from '@/components/PkButton/index.vue'

const roomNo = ref('880123')
const round = ref(8)
const pot = ref('25,600')
onLoad(o => { if (o?.roomNo) roomNo.value = o.roomNo })

const board = ref([
  { suit: 'heart', value: 'K' },
  { suit: 'spade', value: 'Q' },
  { suit: 'club', value: 'J' },
  { suit: 'diamond', value: '10' },
  { suit: 'heart', value: '9' },
])
const players = ref([
  { id: 1, name: '老张', chip: '1.9万', position: 'top-left' },
  { id: 2, name: '小王', chip: '3.2万', position: 'top' },
  { id: 3, name: '莉莉', chip: '8.4千', position: 'top-right' },
  { id: 4, name: '阿强', chip: '6.3千', position: 'bottom-left' },
])
function gift() { uni.navigateTo({ url: '/pages/gift/index' }) }
function chat() { uni.showToast({ title: '聊天（待对接）', icon: 'none' }) }
function exit() { uni.navigateBack() }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.table { @include page-bg; width: 100%; min-height: 100vh; display: flex; flex-direction: column; }
.table__top { display: flex; justify-content: space-between; padding: 16rpx 40rpx; color: #fff; }
.table__tag { background: $gradient-btn-gold; color: #4a3000; font-size: 20rpx; padding: 4rpx 20rpx; border-radius: $radius-full; }
.table__felt {
  flex: 1; position: relative; margin: 0 40rpx;
  background: radial-gradient(ellipse at center, #14356E 0%, #0B1F4A 70%, #071838 100%);
  border-radius: 50% / 45%; border: 8rpx solid #3d2817;
}
.table__pot { position: absolute; top: 8%; left: 50%; transform: translateX(-50%); text-align: center; }
.table__pot-label { color: rgba(255,255,255,0.7); font-size: $font-size-sm; }
.table__pot-value { color: $color-text-gold; font-size: $font-size-xxl; font-weight: bold; }
.table__board { position: absolute; top: 22%; left: 50%; transform: translateX(-50%); display: flex; gap: 12rpx; }
.table__seat { position: absolute; }
.table__seat--top-left { top: 5%; left: 5%; }
.table__seat--top { top: 2%; left: 50%; transform: translateX(-50%); }
.table__seat--top-right { top: 5%; right: 5%; }
.table__seat--bottom-left { bottom: 5%; left: 8%; }
.table__player { display: flex; flex-direction: column; align-items: center; padding: 12rpx 20rpx; background: rgba(0,0,0,0.5); border-radius: $radius-lg; }
.table__name { color: #fff; font-size: $font-size-xs; }
.table__chip { color: $color-text-gold; font-size: $font-size-sm; font-weight: bold; }
.table__actions { display: flex; justify-content: center; gap: 20rpx; padding: 24rpx; }
</style>
