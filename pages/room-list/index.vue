<template>
  <view class="room-list">
    <view class="room-list__header">
      <view class="room-list__back" @click="onBack">
        <text>←</text>
      </view>
      <text class="room-list__title">{{ gameName }}</text>
      <view class="room-list__refresh" @click="onRefresh">
        <text>🔄</text>
      </view>
    </view>

    <PkTab v-model="level" :tabs="levelTabs" @change="onLevelChange" />

    <view v-if="rooms.length" class="room-list__grid">
      <PkCard
        v-for="r in rooms"
        :key="r.id"
        class="room-list__card"
        :active="r.status === 0"
      >
        <view class="room-list__card-top">
          <text class="room-list__no">房号 {{ r.no }}</text>
          <view class="room-list__status" :class="`room-list__status--${statusKey(r.status)}`">
            {{ statusText[r.status] }}
          </view>
        </view>
        <view class="room-list__card-mid">
          <view>
            <text class="room-list__label">底注区间</text>
            <text class="room-list__value">{{ r.blind }}</text>
          </view>
          <view>
            <text class="room-list__label">人数</text>
            <text class="room-list__value">{{ r.players }}/{{ r.capacity }}</text>
          </view>
        </view>
        <PkButton
          size="sm"
          :type="r.players >= r.capacity ? 'gray' : 'gold'"
          :text="r.players >= r.capacity ? '已满' : '进入'"
          :disabled="r.players >= r.capacity"
          @click="enterRoom(r)"
          style="width: 100%"
        />
      </PkCard>
    </view>

    <PkEmpty
      v-else
      title="暂无房间"
      description="该等级下暂无开放房间"
      action-text="刷新列表"
      @action="onRefresh"
    />

    <!-- 加入房间浮动按钮 -->
    <view class="room-list__fab" @click="showJoin = true">
      <text class="room-list__fab-icon">+</text>
    </view>

    <!-- 加入房间弹窗 -->
    <PkDialog
      :visible="showJoin"
      title="加入房间"
      @update:visible="showJoin = $event"
      @confirm="onJoin"
    >
      <view class="room-list__join">
        <PkInput v-model="join.no" placeholder="请输入房间号" style="margin-bottom: 20rpx">
          <template #icon><text>🔑</text></template>
        </PkInput>
        <PkInput v-model="join.pwd" type="password" placeholder="请输入房间密码">
          <template #icon><text>🔒</text></template>
        </PkInput>
        <view class="room-list__quick">
          <view
            v-for="a in quickAmounts"
            :key="a"
            class="room-list__quick-item"
            :class="{ active: join.amount === a }"
            @click="join.amount = a"
          >{{ a }}</view>
        </view>
        <text class="room-list__balance">余额 {{ formatNumber(userStore.credits) }}</text>
      </view>
    </PkDialog>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PkCard from '@/components/PkCard/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import PkTab from '@/components/PkTab/index.vue'
import PkInput from '@/components/PkInput/index.vue'
import PkDialog from '@/components/PkDialog/index.vue'
import PkEmpty from '@/components/PkEmpty/index.vue'
import { api } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { formatNumber } from '@/utils/format.js'

const userStore = useUserStore()
const gameCode = ref('TEXAS')
const gameName = ref('德州竞技')
const level = ref('PRIMARY')
const showJoin = ref(false)
const joining = ref(false)
const join = ref({ no: '', pwd: '', amount: 500 })
const quickAmounts = [200, 500, 1000, 2000]

// gameType -> 牌桌页路由
const tableRouteMap = {
  TEXAS:         '/pages/table-texas/index',
  JINHUA:        '/pages/table-zhajinhua/index',
  SANGONG:       '/pages/table-sangong/index',
  DOUNIU:        '/pages/table-niuniu/index',
  TONGBI_NIUNIU: '/pages/table-tbniuniu/index',
  TONGBI_SANGONG:'/pages/table-tbsangong/index',
}

const levelTabs = [
  { label: '初级房', value: 'PRIMARY' },
  { label: '高级房', value: 'ADVANCED' },
  { label: '顶级房', value: 'PREMIUM' },
]

// status: 0等待 1对局 2结算 3关闭 -> 前端展示
const statusText = { 0: '等待中', 1: '游戏中', 2: '已结算', 3: '已关闭' }
function statusKey(s) { return s === 0 ? 'waiting' : s === 1 ? 'playing' : 'ended' }
const rooms = ref([])

onLoad((options) => {
  if (options?.game) gameCode.value = options.game
  if (options?.name) gameName.value = options.name
  loadRooms()
})

async function loadRooms() {
  try {
    const list = await api.lobby({ gameType: gameCode.value, roomLevel: level.value })
    rooms.value = list.map(r => ({
      id: r.id,
      no: r.roomNo,
      blind: `${r.minBuyin}~${r.maxBuyin}`,
      players: r.currentPlayers,
      capacity: r.maxPlayers,
      status: r.status,
    }))
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
}

function onLevelChange() { loadRooms() }
function onRefresh() {
  loadRooms()
  uni.showToast({ title: '已刷新', icon: 'none' })
}

function enterRoom(r) {
  if (r.players >= r.capacity) return
  const route = tableRouteMap[gameCode.value] || '/pages/table-texas/index'
  uni.navigateTo({ url: `${route}?roomNo=${r.no}` })
}

async function onJoin() {
  if (!join.value.no) return uni.showToast({ title: '请输入房间号', icon: 'none' })
  if (joining.value) return
  joining.value = true
  try {
    await api.joinRoom({
      roomNo: join.value.no,
      password: join.value.pwd,
      buyin: join.value.amount,
    })
    showJoin.value = false
    const route = tableRouteMap[gameCode.value] || '/pages/table-texas/index'
    uni.navigateTo({ url: `${route}?roomNo=${join.value.no}` })
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    joining.value = false
  }
}

function onBack() { uni.navigateBack() }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.room-list {
  background-image: url("/static/generated/room-list-background.png");
  background-size: cover;
  background-position: center;
  @include page-bg;
  width: 100%; min-height: 100vh;
  @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx);
  display: flex; flex-direction: column;

  &__header {
    display: flex; align-items: center;
    margin-bottom: 24rpx;
  }
  &__back, &__refresh {
    width: 64rpx; height: 64rpx;
    display: flex; align-items: center; justify-content: center;
    font-size: 36rpx; color: #fff;
  }
  &__title {
    flex: 1; text-align: center;
    font-size: $font-size-lg; font-weight: bold; color: #fff;
  }

  &__grid {
    flex: 1;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 24rpx;
    margin-top: 24rpx;
    overflow: auto;
  }

  &__card { padding: 24rpx; }

  &__card-top {
    display: flex; justify-content: space-between; align-items: center;
    margin-bottom: 20rpx;
  }
  &__no { color: #fff; font-size: $font-size-md; font-weight: bold; }
  &__status {
    font-size: 20rpx; padding: 2rpx 16rpx; border-radius: $radius-full;
    &--playing { background: rgba(231, 76, 60, 0.25); color: #ff6b6b; }
    &--waiting { background: rgba(46, 204, 113, 0.25); color: #3ed683; }
    &--ended   { background: rgba(154, 168, 204, 0.25); color: #9aa8cc; }
  }

  &__card-mid {
    display: flex; justify-content: space-between;
    margin-bottom: 20rpx;
  }
  &__label { display: block; color: $color-text-secondary; font-size: $font-size-xs; }
  &__value { display: block; color: #fff; font-size: $font-size-md; font-weight: bold; }

  &__fab {
    position: fixed; right: 60rpx; bottom: 60rpx;
    width: 100rpx; height: 100rpx;
    background: $gradient-btn-purple;
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    box-shadow: $shadow-purple-glow;
    color: #fff; font-size: 56rpx;
  }

  &__join { display: flex; flex-direction: column; }
  &__quick {
    display: flex; gap: 16rpx; margin: 20rpx 0;
  }
  &__quick-item {
    padding: 12rpx 24rpx;
    background: $color-bg-card;
    border-radius: $radius-base;
    color: #fff;
    font-size: $font-size-sm;
    &.active { background: $gradient-btn-gold; }
  }
  &__balance {
    text-align: right;
    color: $color-text-secondary; font-size: $font-size-sm;
  }
}
</style>
