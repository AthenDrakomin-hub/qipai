<template>
  <view class="poker-card" :class="`poker-card--${size}`">
    <!-- 牌背 -->
    <view v-if="!faceUp" class="poker-card__back">
      <view class="poker-card__back-pattern" />
    </view>
    <!-- 牌面 -->
    <view v-else class="poker-card__face">
      <view class="poker-card__corner poker-card__corner--top">
        <text class="poker-card__value" :class="suitClass">{{ value }}</text>
        <text class="poker-card__suit" :class="suitClass">{{ suitSymbol }}</text>
      </view>
      <view class="poker-card__center">
        <text class="poker-card__suit-big" :class="suitClass">{{ suitSymbol }}</text>
      </view>
      <view class="poker-card__corner poker-card__corner--bottom">
        <text class="poker-card__value" :class="suitClass">{{ value }}</text>
        <text class="poker-card__suit" :class="suitClass">{{ suitSymbol }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  suit: { type: String, default: 'spade' }, // spade | heart | club | diamond
  value: { type: String, default: 'A' },
  faceUp: { type: Boolean, default: true },
  size: { type: String, default: 'md' }, // sm | md | lg
})

const SUIT_MAP = {
  spade:   { symbol: '♠', color: 'black' },
  heart:   { symbol: '♥', color: 'red' },
  club:    { symbol: '♣', color: 'black' },
  diamond: { symbol: '♦', color: 'red' },
}

const suitSymbol = computed(() => SUIT_MAP[props.suit]?.symbol || '♠')
const suitClass  = computed(() => `poker-card--${SUIT_MAP[props.suit]?.color || 'black'}`)
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.poker-card {
  background: #fff;
  border-radius: $radius-sm;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.4);
  position: relative;
  overflow: hidden;

  &--sm { width: 60rpx;  height: 84rpx; }
  &--md { width: 90rpx;  height: 126rpx; }
  &--lg { width: 120rpx; height: 168rpx; }

  &__face {
    width: 100%;
    height: 100%;
    position: relative;
    padding: 6rpx;
    box-sizing: border-box;
  }

  &__corner {
    position: absolute;
    display: flex;
    flex-direction: column;
    align-items: center;
    line-height: 1;

    &--top { top: 6rpx; left: 8rpx; }
    &--bottom { bottom: 6rpx; right: 8rpx; transform: rotate(180deg); }
  }

  &__value {
    font-size: 18rpx;
    font-weight: bold;
    font-family: $font-family-numeric;
  }

  &__suit {
    font-size: 16rpx;
  }

  &__center {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__suit-big { font-size: 36rpx; }

  &--black .poker-card__value,
  &--black .poker-card__suit,
  &--black .poker-card__suit-big { color: #1a1a1a; }

  &--red .poker-card__value,
  &--red .poker-card__suit,
  &--red .poker-card__suit-big { color: $color-error; }

  &__back {
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, #1e3260, #142554);
    border-radius: $radius-sm;
    border: 4rpx solid #fff;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__back-pattern {
    width: 70%;
    height: 70%;
    background:
      repeating-linear-gradient(45deg,
        rgba(123, 104, 238, 0.3) 0 4rpx,
        transparent 4rpx 8rpx);
    border-radius: $radius-xs;
  }
}
</style>
