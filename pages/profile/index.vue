<template>
  <view class="profile">
    <view class="profile__header">
      <view class="profile__back" @click="onBack"><text>←</text></view>
      <text class="profile__title">个人中心</text>
    </view>

    <view class="profile__body">
      <!-- 左：用户信息 -->
      <view class="profile__user">
        <PkAvatar :src="user.avatar" :name="user.name" :vip="user.vip" :size="120" />
        <view class="profile__user-meta">
          <view class="profile__name-row">
            <text class="profile__name">{{ user.name }}</text>
            <view v-if="user.isAgent" class="profile__agent-tag">代理</view>
          </view>
          <text class="profile__id">ID: {{ user.id }}</text>
          <view class="profile__balance">
            <text class="profile__balance-label">余额</text>
            <text class="profile__balance-value">{{ formatNumber(user.balance) }}</text>
          </view>
        </view>
      </view>

      <view class="profile__quick">
        <PkButton type="green" text="充值" size="sm" @click="onQuick('/pages/pay/index')" />
        <PkButton type="red"   text="提现" size="sm" @click="onQuick('/pages/withdraw/index')" />
        <PkButton type="blue"  text="明细" size="sm" @click="onQuick('/pages/flow/index')" />
      </view>

      <!-- 右：菜单 -->
      <view class="profile__menu">
        <view
          v-for="m in menus"
          :key="m.key"
          class="profile__menu-item"
          @click="onMenu(m)"
        >
          <text class="profile__menu-icon">{{ m.icon }}</text>
          <text class="profile__menu-label">{{ m.label }}</text>
          <view v-if="m.badge" class="profile__menu-badge">{{ m.badge }}</view>
          <text class="profile__menu-arrow">›</text>
        </view>
      </view>
    </view>

    <view class="profile__footer">
      <PkButton type="red" text="退出登录" @click="onLogout" />
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import PkAvatar from '@/components/PkAvatar/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { useUserStore } from '@/stores/user.js'
import { formatNumber } from '@/utils/format.js'

const userStore = useUserStore()

const user = computed(() => ({
  name: userStore.nickname,
  id: (userStore.state.info && userStore.state.info.id) || '',
  avatar: userStore.avatar,
  vip: (userStore.state.info && userStore.state.info.role) || 0,
  balance: userStore.credits,
  isAgent: userStore.isAgent,
}))

const menus = [
  { key: 'pay',     label: '充值',     icon: '💳', path: '/pages/pay/index' },
  { key: 'withdraw',label: '提现',     icon: '🏧', path: '/pages/withdraw/index' },
  { key: 'record',  label: '战绩记录', icon: '📊', path: '/pages/records/index' },
  { key: 'flow',    label: '资金流水', icon: '💰', path: '/pages/flow/index' },
  { key: 'msg',     label: '消息通知', icon: '🔔', path: '/pages/messages/index' },
  { key: 'invite',  label: '邀请好友', icon: '🎁', path: '/pages/invite/index' },
  { key: 'myroom',  label: '我的房间', icon: '🏠', path: '/pages/my-rooms/index' },
  { key: 'agent',   label: '代理中心', icon: '👑', path: '/pages/agent/index' },
  { key: 'service', label: '联系客服', icon: '🎧', path: '/pages/service/index' },
  { key: 'setting', label: '设置',     icon: '⚙️', path: '/pages/setting/index' },
]

onMounted(() => {
  userStore.fetchMe().catch(() => {})
})

function onQuick(path) { uni.navigateTo({ url: path }) }
function onMenu(m) {
  if (m.path) uni.navigateTo({ url: m.path })
}
function onBack() { uni.navigateBack() }
function onLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await userStore.logout()
      } finally {
        uni.reLaunch({ url: '/pages/login/index' })
      }
    },
  })
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.profile {
  background-image: url("/static/generated/profile-background.png");
  background-size: cover;
  background-position: center;
  @include page-bg;
  width: 100%; min-height: 100vh;
  @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx);
  display: flex; flex-direction: column;

  &__header {
    display: flex; align-items: center;
    margin-bottom: 32rpx;
  }
  &__back {
    width: 64rpx; height: 64rpx;
    display: flex; align-items: center; justify-content: center;
    color: #fff; font-size: 36rpx;
  }
  &__title {
    flex: 1; text-align: center;
    font-size: $font-size-lg; font-weight: bold; color: #fff;
  }

  &__body {
    flex: 1;
    display: flex; gap: 40rpx;
  }

  &__user {
    width: 480rpx;
    @include card;
    padding: 40rpx;
    display: flex; flex-direction: column; align-items: center; gap: 24rpx;
  }
  &__user-meta { text-align: center; }
  &__name-row { display: flex; align-items: center; gap: 12rpx; justify-content: center; }
  &__name { color: #fff; font-size: $font-size-lg; font-weight: bold; }
  &__agent-tag {
    background: $gradient-btn-gold; color: #4a3000;
    font-size: 20rpx; padding: 2rpx 12rpx; border-radius: $radius-full;
  }
  &__id { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-top: 8rpx; }
  &__balance { margin-top: 24rpx; }
  &__balance-label { display: block; color: $color-text-secondary; font-size: $font-size-xs; }
  &__balance-value {
    display: block; color: $color-text-gold;
    font-size: $font-size-xxl; font-weight: bold;
    font-family: $font-family-numeric;
  }

  &__quick {
    margin-top: 24rpx;
    display: flex; gap: 20rpx;
  }

  &__menu {
    flex: 1;
    display: flex; flex-direction: column;
    gap: 16rpx;
  }
  &__menu-item {
    @include card;
    padding: 24rpx 32rpx;
    display: flex; align-items: center; gap: 20rpx;
    cursor: pointer;
    &:active { background: $color-bg-card-hover; }
  }
  &__menu-icon { font-size: 36rpx; }
  &__menu-label { flex: 1; color: #fff; font-size: $font-size-md; }
  &__menu-badge {
    background: $color-error; color: #fff;
    font-size: 20rpx; padding: 2rpx 12rpx; border-radius: $radius-full;
  }
  &__menu-arrow { color: $color-text-secondary; font-size: 36rpx; }

  &__footer {
    padding: 24rpx 0;
    display: flex; justify-content: center;
  }
}
</style>
