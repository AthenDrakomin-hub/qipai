<template>
  <view class="pk-tab">
    <view
      v-for="tab in tabs"
      :key="tab.value"
      class="pk-tab__item"
      :class="{ 'pk-tab__item--active': tab.value === modelValue }"
      @click="onSelect(tab.value)"
    >
      <text class="pk-tab__text">{{ tab.label }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  tabs: { type: Array, required: true }, // [{ label, value }]
  modelValue: { type: [String, Number], default: '' },
})
const emit = defineEmits(['update:modelValue', 'change'])

function onSelect(value) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-tab {
  display: inline-flex;
  background: $color-bg-panel;
  border: $border-width-thin solid $color-border-light;
  border-radius: $radius-full;
  padding: 6rpx;

  &__item {
    padding: 12rpx 36rpx;
    border-radius: $radius-full;
    transition: all 0.2s;

    &--active {
      background: $gradient-btn-gold;
      box-shadow: $shadow-gold-glow;

      .pk-tab__text { color: $color-text-primary; font-weight: $font-weight-bold; }
    }
  }

  &__text {
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
}
</style>
