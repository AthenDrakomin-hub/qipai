<template>
  <view class="hall">
    <!-- 顶部栏 -->
    <view class="hall__topbar">
      <view class="hall__user">
        <PkAvatar :src="user.avatar" :name="user.name" :vip="user.vip" />
        <view class="hall__user-meta">
          <text class="hall__nick">{{ user.name }}</text>
          <view class="hall__balance">
            <text class="hall__coin">🪙 {{ formatNumber(user.balance) }}</text>
          </view>
        </view>
        <view v-if="isAgent" class="hall__agent-tag">代理</view>
      </view>

      <view class="hall__notice">
        <text>{{ notice }}</text>
      </view>

      <view class="hall__msg">
        <text class="hall__msg-icon">🔔</text>
        <view v-if="unread > 0" class="hall__msg-dot">{{ unread }}</view>
      </view>
    </view>

    <view class="hall__main">
      <!-- 左侧在线玩家 -->
      <view class="hall__online">
        <text class="hall__online-title">在线玩家 ({{ onlineList.length }})</text>
        <view v-for="p in onlineList" :key="p.id" class="hall__online-item">
          <PkAvatar :src="p.avatar" :name="p.name" :size="56" />
          <view class="hall__online-meta">
            <text class="hall__online-name">{{ p.name }}</text>
            <text class="hall__online-coin">{{ formatNumber(p.balance) }}</text>
          </view>
        </view>
      </view>

      <!-- 中间大厅 -->
      <view class="hall__center">
        <image class="hall__host" src="/static/generated/hall-host.png" mode="aspectFit" />
        <view class="hall__games">
          <view
            v-for="g in games"
            :key="g.id"
            class="hall__game"
            :class="`hall__game--${g.color}`"
            @click="enterGame(g)"
          >
            <view class="hall__game-icon">{{ g.icon }}</view>
            <text class="hall__game-name">{{ g.name }}</text>
            <view class="hall__game-btn">进入</view>
          </view>
        </view>

        <view v-if="isAgent" class="hall__agent-actions">
          <PkButton type="purple" text="+ 创建房间" @click="onCreateRoom" />
          <PkButton type="blue" text="我的房间" @click="onMyRooms" />
        </view>
      </view>

      <!-- 右侧场地入口 -->
      <view class="hall__arenas">
        <view
          v-for="a in arenas"
          :key="a.id"
          class="hall__arena"
          :class="`hall__arena--${a.color}`"
          @click="enterArena(a)"
        >
          <text class="hall__arena-icon">{{ a.icon }}</text>
          <text class="hall__arena-name">{{ a.name }}</text>
        </view>
      </view>
    </view>

    <!-- 底部导航 -->
    <view class="hall__tabbar">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="hall__tab"
        :class="{ active: t.key === activeTab }"
        @click="onTabClick(t)"
      >
        <text class="hall__tab-icon">{{ t.icon }}</text>
        <text class="hall__tab-text">{{ t.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import PkAvatar from '@/components/PkAvatar/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { useUserStore } from '@/stores/user.js'
import { formatNumber } from '@/utils/format.js'

const userStore = useUserStore()

// 游戏入口静态配置（gameType code 对齐后端枚举）
const games = ref([
  { id: 'texas',     code: 'TEXAS',         name: '德州扑克', icon: '♠️', color: 'gold' },
  { id: 'zhajinhua', code: 'JINHUA',        name: '炸金花',   icon: '🃏', color: 'purple' },
  { id: 'sangong',   code: 'SANGONG',       name: '抢庄三公', icon: '🎴', color: 'blue' },
  { id: 'nn',        code: 'DOUNIU',        name: '抢庄牛牛', icon: '🐂', color: 'green' },
  { id: 'tbnn',      code: 'TONGBI_NIUNIU', name: '通比牛牛', icon: '🐃', color: 'gold' },
  { id: 'tbsg',      code: 'TONGBI_SANGONG',name: '通比三公', icon: '🎲', color: 'purple' },
])

const arenas = ref([
  { id: 'normal',  name: '普通场', icon: '🎰', color: 'gold' },
  { id: 'hundred', name: '百人场', icon: '👥', color: 'purple' },
  { id: 'match',   name: '赛事场', icon: '🏆', color: 'blue' },
])

const tabs = ref([
  { key: 'hall',   label: '大厅', icon: '🏛️' },
  { key: 'record', label: '战绩', icon: '📊' },
  { key: 'me',     label: '我的', icon: '👤' },
])
const activeTab = ref('hall')

const user = computed(() => ({
  name: userStore.nickname,
  avatar: userStore.avatar,
  vip: (userStore.state.info && userStore.state.info.role) || 0,
  balance: userStore.credits,
}))
const isAgent = computed(() => userStore.isAgent)
const notice = ref('欢迎来到 V-POKER')
const unread = ref(3)
const onlineList = ref([])

onMounted(async () => {
  try {
    await userStore.fetchMe()
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
  // 在线玩家列表（mock 占位，后端提供 /user/online 后替换）
  onlineList.value = [
    { id: 1, name: 'Andy', balance: 90400 },
    { id: 2, name: '老张', balance: 19000 },
    { id: 3, name: '小王', balance: 32000 },
  ]
})

function enterGame(g) {
  uni.navigateTo({ url: `/pages/room-list/index?game=${g.code}&name=${g.name}` })
}
function enterArena(a) { uni.showToast({ title: `进入${a.name}（待对接）`, icon: 'none' }) }
function onCreateRoom() { uni.navigateTo({ url: '/pages/create-room/index' }) }
function onMyRooms()     { uni.navigateTo({ url: '/pages/my-rooms/index' }) }
function onTabClick(t) {
  activeTab.value = t.key
  if (t.key === 'record') uni.navigateTo({ url: '/pages/records/index' })
  else if (t.key === 'me') uni.navigateTo({ url: '/pages/profile/index' })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.hall {
  @include page-bg;
  width: 100%; min-height: 100vh;
  display: flex; flex-direction: column;
  background-image: url("/static/generated/hall-background.png");
  background-size: cover;
  background-position: center;

  &__topbar {
    display: flex; align-items: center;
    padding: 16rpx 40rpx;
    gap: 32rpx;
  }

  &__user { display: flex; align-items: center; gap: 16rpx; min-width: 320rpx; }
  &__user-meta { display: flex; flex-direction: column; }
  &__nick { color: #fff; font-weight: bold; font-size: $font-size-md; }
  &__coin { color: $color-text-gold; font-size: $font-size-sm; }
  &__agent-tag {
    background: $gradient-btn-gold; color: #4a3000;
    font-size: 18rpx; padding: 2rpx 12rpx; border-radius: $radius-full;
    font-weight: bold;
  }

  &__notice {
    flex: 1;
    background: rgba(230, 194, 90, 0.12);
    border: $border-width-thin solid $color-border-gold;
    border-radius: $radius-full;
    padding: 10rpx 32rpx;
    color: $color-text-gold;
    font-size: $font-size-sm;
    text-align: center;
  }

  &__msg { position: relative; font-size: 36rpx; }
  &__msg-dot {
    position: absolute; top: -8rpx; right: -12rpx;
    background: $color-error; color: #fff;
    font-size: 18rpx; padding: 2rpx 10rpx; border-radius: $radius-full;
  }

  &__main {
    flex: 1;
    display: flex;
    padding: 0 40rpx;
    gap: 32rpx;
    min-height: 0;
  }

  &__online {
    width: 260rpx;
    background: $color-bg-panel;
    border-radius: $radius-lg;
    padding: 20rpx;
    overflow: hidden;
  }
  &__online-title { color: $color-text-secondary; font-size: $font-size-xs; display: block; margin-bottom: 12rpx; }
  &__online-item {
    display: flex; align-items: center; gap: 12rpx;
    padding: 10rpx 0;
  }
  &__online-meta { display: flex; flex-direction: column; }
  &__online-name { color: #fff; font-size: $font-size-sm; }
  &__online-coin { color: $color-text-secondary; font-size: 20rpx; }

  &__center {
    flex: 1;
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    gap: 18rpx;
    position: relative;
  }

  &__host {
    position: absolute;
    left: 50%;
    bottom: -32rpx;
    transform: translateX(-50%);
    width: 620rpx;
    height: 780rpx;
    opacity: 0.96;
    pointer-events: none;
  }
  &__games {
    position: relative;
    z-index: 1;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 24rpx;
  }
  &__game {
    width: 220rpx; height: 200rpx;
    backdrop-filter: blur(8rpx);
    background: $color-bg-panel;
    border: $border-width-thin solid $color-border-gold;
    border-radius: $radius-lg;
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    gap: 12rpx;
    cursor: pointer;
    transition: transform 0.15s;
    &:active { transform: scale(0.95); }
  }
  &__game-icon { font-size: 56rpx; }
  &__game-name { color: #fff; font-size: $font-size-sm; }
  &__game-btn {
    background: $gradient-btn-gold;
    color: #fff; font-size: 20rpx; font-weight: bold;
    padding: 4rpx 24rpx; border-radius: $radius-full;
  }

  &__agent-actions { display: flex; gap: 20rpx; }

  &__arenas {
    width: 280rpx;
    display: flex; flex-direction: column;
    gap: 24rpx;
    justify-content: center;
  }
  &__arena {
    height: 140rpx;
    border-radius: $radius-lg;
    display: flex; align-items: center; justify-content: center;
    gap: 16rpx;
    border: $border-width-thin solid $color-border-gold;
    cursor: pointer;
    &:active { transform: scale(0.97); }
  }
  &__arena--gold   { background: linear-gradient(135deg, #f0d070, #9e8142); }
  &__arena--purple { background: linear-gradient(135deg, #8b7cf6, #5b4bc4); }
  &__arena--blue   { background: linear-gradient(135deg, #4fa8e8, #2e6fb8); }
  &__arena-icon { font-size: 44rpx; }
  &__arena-name { color: #fff; font-size: $font-size-md; font-weight: bold; }

  &__tabbar {
    height: 100rpx;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
  }
  &__tab {
    flex: 1;
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    gap: 4rpx;
    &.active .hall__tab-text { color: $color-text-gold; }
  }
  &__tab-icon { font-size: 32rpx; }
  &__tab-text { color: $color-text-secondary; font-size: $font-size-xs; }
}
</style>
