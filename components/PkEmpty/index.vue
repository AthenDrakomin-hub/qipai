<template>
  <view class="pk-empty">
    <view class="pk-empty__illustration">
      <view class="pk-empty__box" />
    </view>
    <text class="pk-empty__title">{{ title }}</text>
    <text v-if="description" class="pk-empty__desc">{{ description }}</text>
    <view v-if="actionText" class="pk-empty__action" @click="$emit('action')">
      <text class="pk-empty__action-text">{{ actionText }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' },
})
defineEmits(['action'])
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 40rpx;
  gap: 16rpx;

  &__illustration {
    width: 200rpx;
    height: 200rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.04);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24rpx;
  }

  &__box {
    width: 100rpx;
    height: 80rpx;
    border: 4rpx solid $color-text-placeholder;
    border-radius: $radius-sm;
    opacity: 0.5;
  }

  &__title {
    font-size: $font-size-lg;
    color: $color-text-secondary;
    font-weight: $font-weight-medium;
  }

  &__desc {
    font-size: $font-size-sm;
    color: $color-text-placeholder;
  }

  &__action {
    margin-top: 32rpx;
    padding: 20rpx 56rpx;
    background: $gradient-btn-gold;
    border-radius: $radius-full;
    box-shadow: $shadow-gold-glow;
  }

  &__action-text {
    color: #fff;
    font-weight: $font-weight-bold;
  }
}
</style>
