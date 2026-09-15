<template>
  <view class="pk-card" :class="{ 'pk-card--active': active }">
    <view v-if="title" class="pk-card__header">
      <text class="pk-card__title">{{ title }}</text>
      <view v-if="$slots.extra" class="pk-card__extra"><slot name="extra" /></view>
    </view>
    <view class="pk-card__body" :style="{ padding: bodyPadding }">
      <slot />
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  active: { type: Boolean, default: false },
  padding: { type: String, default: '32rpx' },
})

const bodyPadding = computed(() => props.padding)
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-card {
  background: $color-bg-panel;
  border: $border-width-thin solid $color-border-gold;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
  overflow: hidden;

  &--active {
    border-color: $color-border-gold-strong;
    box-shadow: $shadow-card, $shadow-gold-glow;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 24rpx 32rpx;
    border-bottom: $border-width-thin solid $color-border-light;
  }

  &__title {
    font-size: $font-size-md;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
}
</style>
