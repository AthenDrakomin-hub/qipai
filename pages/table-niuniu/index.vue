<template>
  <view class="table">
    <view class="table__top">
      <text>房间 {{ roomNo }}</text><text>第{{ currentRound }}局/共{{ totalRound }}局</text>
    </view>
    <view class="felt">
      <view class="pot"><text>奖池</text><text class="num">{{ formatAmount(pot) }}</text></view>
      <view class="prompt">请选择幸运区域</view>
      <view v-for="p in players" :key="p.id" class="seat" :class="p.pos">
        <view class="player">
          <PkAvatar :name="p.name" :size="64" />
          <text class="name">{{ p.name }}</text>
          <text class="chip">{{ p.chip }}</text>
        </view>
      </view>
      <view class="areas">
        <view
          v-for="a in areas"
          :key="a.key"
          class="area"
          :class="[a.key, { active: selectedArea === a.key }]"
          @click="chooseArea(a)"
        >{{ a.label }}</view>
      </view>
    </view>
    <view class="bottom">
      <view class="chips">
        <PkButton size="md" type="green" text="100" @click="act('下注 100')" />
        <PkButton size="md" type="green" text="1000" @click="act('下注 1000')" />
        <PkButton size="md" type="green" text="1万" @click="act('下注 1万')" />
        <PkButton size="md" type="green" text="10万" @click="act('下注 10万')" />
        <PkButton size="md" type="green" text="续投" @click="act('续投')" />
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

const roomNo = ref('550123')
const roomId = ref(401)
const currentRound = ref(2)
const totalRound = ref(25)
const pot = ref(86000)
const acting = ref(false)
const selectedArea = ref('')
const areas = ref([
  { key: 'cowboy', label: '牛仔胜利 2倍' },
  { key: 'tie',    label: '平手 19倍' },
  { key: 'bull',   label: '公牛胜利 2倍' },
  { key: 'any',    label: '任一人手牌' },
  { key: 'win',    label: '获胜牌型' },
])

onLoad((options) => {
  if (options?.roomNo) roomNo.value = options.roomNo
  if (options?.roomId) roomId.value = options.roomId
})

const players = ref([
  { id: 1, name: '玩家A', chip: '44.76万', pos: 'tl' },
  { id: 2, name: '玩家B', chip: '12.3万',  pos: 'bl' },
  { id: 3, name: '玩家C', chip: '8.9万',   pos: 'tr' },
  { id: 4, name: 'iPad(2)', chip: '44.76万', pos: 'self' },
])

function chooseArea(a) {
  selectedArea.value = a.key
  uni.showToast({ title: `已选择 ${a.label}`, icon: 'none' })
}

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
  background: linear-gradient(180deg, #8B6F47 0%, #6B5335 100%);
  border-radius: $radius-lg; border: 8rpx solid #5a4228;
}
.pot { position: absolute; top: 6%; left: 50%; transform: translateX(-50%); text-align: center; color: #fff; }
.pot .num { display: block; color: #f5d76e; font-size: $font-size-xxl; font-weight: bold; }
.prompt { position: absolute; top: 16%; left: 50%; transform: translateX(-50%); color: #fff; font-size: $font-size-md; }
.seat { position: absolute; }
.seat.tl { top: 8%; left: 6%; } .seat.bl { bottom: 8%; left: 10%; }
.seat.tr { top: 8%; right: 6%; } .seat.self { bottom: 2%; left: 50%; transform: translateX(-50%); }
.player { display: flex; flex-direction: column; align-items: center; padding: 12rpx 20rpx; background: rgba(0,0,0,0.5); border-radius: $radius-lg; }
.name { color: #fff; font-size: $font-size-xs; }
.chip { color: #f5d76e; font-size: $font-size-sm; font-weight: bold; }
.areas {
  position: absolute; top: 35%; left: 50%; transform: translateX(-50%);
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; width: 80%;
}
.area {
  padding: 24rpx; background: rgba(255,255,255,0.9);
  border-radius: $radius-base; text-align: center;
  color: #6b5335; font-weight: bold;
}
.area.active { border: 4rpx solid $color-brand-gold; box-shadow: $shadow-gold-glow; }
.bottom { padding: 16rpx 40rpx; }
.chips { display: flex; justify-content: center; gap: 16rpx; }
</style>
