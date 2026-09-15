<template>
  <view class="pk-avatar" :style="{ width: size + 'rpx', height: size + 'rpx' }">
    <image v-if="src" class="pk-avatar__img" :src="src" mode="aspectFill" />
    <view v-else class="pk-avatar__fallback">
      <text class="pk-avatar__initial">{{ initial }}</text>
    </view>
    <view v-if="vip" class="pk-avatar__vip">
      <text class="pk-avatar__vip-text">VIP{{ vip }}</text>
    </view>
    <view v-if="online" class="pk-avatar__online" />
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: Number, default: 96 },
  vip: { type: [Number, String], default: 0 },
  online: { type: Boolean, default: false },
})

const initial = computed(() => (props.name ? props.name.charAt(0).toUpperCase() : '?'))
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-avatar {
  position: relative;
  border-radius: 50%;
  overflow: visible;
  border: 4rpx solid $color-border-gold;
  flex-shrink: 0;

  &__img,
  &__fallback {
    width: 100%;
    height: 100%;
    border-radius: 50%;
  }

  &__fallback {
    background: linear-gradient(135deg, #4a5a82, #2e3d5e);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__initial {
    color: #fff;
    font-size: 32rpx;
    font-weight: bold;
  }

  &__vip {
    position: absolute;
    bottom: -8rpx;
    left: 50%;
    transform: translateX(-50%);
    background: linear-gradient(180deg, #f0d070, #c9a961);
    border-radius: $radius-full;
    padding: 2rpx 12rpx;
    white-space: nowrap;
  }

  &__vip-text {
    font-size: 16rpx;
    color: #4a3000;
    font-weight: bold;
  }

  &__online {
    position: absolute;
    top: 4rpx;
    right: 4rpx;
    width: 16rpx;
    height: 16rpx;
    background: #2ecc71;
    border: 2rpx solid #fff;
    border-radius: 50%;
  }
}
</style>
