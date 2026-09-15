<template>
  <view class="chip-stack" :style="{ width: size + 'rpx' }">
    <view
      v-for="(i, idx) in layers"
      :key="idx"
      class="chip-stack__chip"
      :class="`chip-stack__chip--${color}`"
      :style="{ bottom: idx * 14 + 'rpx' }"
    >
      <view class="chip-stack__inner" />
    </view>
    <text v-if="amount" class="chip-stack__amount">{{ amount }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  amount: { type: [Number, String], default: '' },
  color: { type: String, default: 'purple' }, // purple | red | green | blue | gold
  size: { type: Number, default: 80 },
  layers: { type: Number, default: 3 },
})
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.chip-stack {
  position: relative;
  height: 100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;

  &__chip {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    width: 100%;
    height: 28rpx;
    border-radius: 50%;
    border: 4rpx dashed rgba(255, 255, 255, 0.6);
  }

  &__inner {
    width: 100%;
    height: 100%;
    border-radius: 50%;
  }

  &__chip--purple .chip-stack__inner { background: radial-gradient(circle, #9d8fff, #5b4bc4); }
  &__chip--red    .chip-stack__inner { background: radial-gradient(circle, #ff6b5b, #c0392b); }
  &__chip--green  .chip-stack__inner { background: radial-gradient(circle, #3ed683, #27ae60); }
  &__chip--blue   .chip-stack__inner { background: radial-gradient(circle, #4fa8e8, #2e6fb8); }
  &__chip--gold   .chip-stack__inner { background: radial-gradient(circle, #f0d070, #c9a961); }

  &__amount {
    position: absolute;
    top: -36rpx;
    left: 50%;
    transform: translateX(-50%);
    font-size: $font-size-sm;
    color: $color-text-gold;
    font-weight: bold;
    white-space: nowrap;
  }
}
</style>
