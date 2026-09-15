<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">我的房间</text>
    </view>

    <PkTab v-model="filter" :tabs="tabs" />

    <view v-if="filteredRooms.length" class="grid">
      <PkCard v-for="r in filteredRooms" :key="r.id" class="item">
        <view class="item__head">
          <text class="item__no">{{ r.no }}</text>
          <view class="item__status" :class="r.status">{{ statusText[r.status] }}</view>
        </view>
        <text class="item__game">{{ r.icon }} {{ r.game }} · {{ r.type }}</text>
        <text class="item__round">第 {{ r.round }}/{{ r.total }} 局 · {{ r.players }}/{{ r.cap }}人</text>
        <view class="item__btns">
          <PkButton size="sm" type="blue" text="进入旁观" @click="spectate(r)" />
          <PkButton size="sm" type="ghost" text="查看结算" @click="viewSettlement(r)" />
          <PkButton size="sm" type="red" text="关闭房间" @click="close(r)" />
        </view>
      </PkCard>
    </view>
    <PkEmpty
      v-else
      title="暂无房间"
      :description="filter === 'open' ? '当前没有进行中的房间' : '暂无已结束的房间'"
    />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import PkTab from '@/components/PkTab/index.vue'
import PkEmpty from '@/components/PkEmpty/index.vue'
import { api } from '@/api/index.js'

const filter = ref('open')
const tabs = [
  { label: '进行中', value: 'open' },
  { label: '已结束', value: 'closed' },
]
const statusText = { open: '进行中', closed: '已结束' }

const GAME_NAME = {
  TEXAS: '德州扑克', JINHUA: '炸金花', SANGONG: '抢庄三公',
  DOUNIU: '抢庄牛牛', TONGBI_NIUNIU: '通比牛牛', TONGBI_SANGONG: '通比三公',
}
const GAME_ICON = {
  TEXAS: '♠️', JINHUA: '🃏', SANGONG: '🎴',
  DOUNIU: '🐂', TONGBI_NIUNIU: '🐃', TONGBI_SANGONG: '🎲',
}
const LEVEL_NAME = { PRIMARY: '初级房', ADVANCED: '高级房', PREMIUM: '顶级房' }

const rooms = ref([])
const filteredRooms = computed(() => rooms.value.filter(r => r.status === filter.value))

onMounted(loadRooms)

async function loadRooms() {
  try {
    const list = await api.myOwnedRooms()
    rooms.value = list.map(r => ({
      id: r.id,
      no: r.roomNo,
      game: GAME_NAME[r.gameType] || r.gameType,
      icon: GAME_ICON[r.gameType] || '🎮',
      type: LEVEL_NAME[r.roomLevel] || r.roomLevel,
      round: r.currentRound,
      total: r.totalRounds,
      players: r.currentPlayers,
      cap: r.maxPlayers,
      status: r.status === 2 || r.status === 3 ? 'closed' : 'open',
    }))
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
}

function back() { uni.navigateBack() }
function spectate(r) { uni.navigateTo({ url: `/pages/spectate/index?roomNo=${r.no}` }) }
function viewSettlement(r) { uni.showToast({ title: `房间 ${r.no} 结算页开发中`, icon: 'none' }) }
function close(r) {
  uni.showModal({
    title: '提示',
    content: `确定关闭房间 ${r.no}？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await api.closeRoom(r.id)
        uni.showToast({ title: '已关闭', icon: 'success' })
        loadRooms()
      } catch (e) {
        uni.showToast({ title: e.message, icon: 'none' })
      }
    },
  })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.grid { margin-top: 24rpx; display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
.item { padding: 24rpx; display: flex; flex-direction: column; gap: 12rpx; }
.item__head { display: flex; justify-content: space-between; align-items: center; }
.item__no { color: #fff; font-size: $font-size-md; font-weight: bold; }
.item__status { font-size: 20rpx; padding: 2rpx 12rpx; border-radius: $radius-full; background: rgba(46,204,113,0.25); color: #3ed683; }
.item__status.closed { background: rgba(154,168,204,0.25); color: #9aa8cc; }
.item__game { color: #fff; font-size: $font-size-sm; }
.item__round { color: $color-text-secondary; font-size: $font-size-xs; }
.item__btns { display: flex; flex-direction: column; gap: 8rpx; margin-top: 8rpx; }
</style>
