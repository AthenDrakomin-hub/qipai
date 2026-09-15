<template>
  <view class="pk-input" :class="{ 'pk-input--error': !!error, 'pk-input--focus': focused }">
    <view v-if="$slots.icon" class="pk-input__icon">
      <slot name="icon" />
    </view>
    <input
      class="pk-input__inner"
      :type="inputType"
      :value="modelValue"
      :placeholder="placeholder"
      :placeholder-style="`color: #5A6B94`"
      :password="type === 'password'"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
    />
    <view v-if="$slots.suffix" class="pk-input__suffix" @click="$emit('suffix-click')">
      <slot name="suffix" />
    </view>
    <text v-if="error" class="pk-input__error">{{ error }}</text>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' }, // text | password | number
  placeholder: { type: String, default: '' },
  error: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'suffix-click'])

const focused = ref(false)
const inputType = computed(() => (props.type === 'number' ? 'number' : 'text'))

function onInput(e) {
  emit('update:modelValue', e.detail.value)
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-input {
  position: relative;
  display: flex;
  align-items: center;
  background: $color-bg-input;
  border: $border-width-thin solid $color-border-light;
  border-radius: $radius-base;
  padding: 0 28rpx;
  height: 88rpx;
  transition: border-color 0.2s;

  &--focus { border-color: $color-border-gold-strong; box-shadow: $shadow-focus; }
  &--error { border-color: $color-error; }

  &__icon {
    margin-right: 20rpx;
    color: $color-text-secondary;
  }

  &__inner {
    flex: 1;
    height: 100%;
    color: $color-text-primary;
    font-size: $font-size-base;
  }

  &__suffix { margin-left: 16rpx; }

  &__error {
    position: absolute;
    top: 100%;
    left: 0;
    margin-top: 8rpx;
    font-size: $font-size-xs;
    color: $color-error;
    white-space: nowrap;
  }
}
</style>
