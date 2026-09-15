<template>
  <view
    class="pk-button"
    :class="[
      `pk-button--${type}`,
      `pk-button--${size}`,
      {
        'pk-button--block': block,
        'pk-button--disabled': disabled || loading,
        'pk-button--round': round,
      },
    ]"
    @click="handleClick"
  >
    <view v-if="loading" class="pk-button__loading" />
    <text v-else class="pk-button__text">{{ text }}</text>
    <slot />
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  type: {
    type: String,
    default: 'gold', // gold | red | green | purple | blue | gray | ghost
    validator: v => ['gold', 'red', 'green', 'purple', 'blue', 'gray', 'ghost'].includes(v),
  },
  size: {
    type: String,
    default: 'md', // sm | md | lg
    validator: v => ['sm', 'md', 'lg'].includes(v),
  },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  block: { type: Boolean, default: false },
  round: { type: Boolean, default: true },
})

const emit = defineEmits(['click'])

function handleClick(e) {
  if (props.disabled || props.loading) return
  emit('click', e)
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 48rpx;
  border-radius: $radius-full;
  font-weight: $font-weight-bold;
  color: $color-text-primary;
  box-shadow: $shadow-btn;
  transition: transform 0.1s ease, opacity 0.2s ease;
  user-select: none;

  &:active {
    transform: scale(0.96);
    filter: brightness(0.92);
  }

  &--block {
    display: flex;
    width: 100%;
  }

  &--disabled {
    opacity: 0.5;
    pointer-events: none;
  }

  &--gold   { background: $gradient-btn-gold;   box-shadow: $shadow-btn, $shadow-gold-glow; }
  &--red    { background: $gradient-btn-red; }
  &--green  { background: $gradient-btn-green; }
  &--purple { background: $gradient-btn-purple; box-shadow: $shadow-btn, $shadow-purple-glow; }
  &--blue   { background: $gradient-btn-blue; }
  &--gray   { background: $gradient-btn-gray; color: $color-text-regular; }
  &--ghost  { background: transparent; border: $border-width-thin solid $color-border-gold; color: $color-text-gold; }

  &--sm { height: 64rpx;  font-size: $font-size-sm; padding: 0 28rpx; }
  &--md { height: 88rpx;  font-size: $font-size-base; }
  &--lg { height: 108rpx; font-size: $font-size-md; padding: 0 64rpx; }

  &__text { line-height: 1; }

  &__loading {
    width: 32rpx;
    height: 32rpx;
    border: 4rpx solid rgba(255, 255, 255, 0.3);
    border-top-color: #fff;
    border-radius: 50%;
    animation: pk-btn-spin 0.8s linear infinite;
  }
}

@keyframes pk-btn-spin {
  to { transform: rotate(360deg); }
}
</style>
