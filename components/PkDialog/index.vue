<template>
  <view v-if="visible" class="pk-dialog" @click.self="onMaskClick">
    <view class="pk-dialog__panel">
      <view class="pk-dialog__header">
        <text class="pk-dialog__title">{{ title }}</text>
        <view class="pk-dialog__close" @click="onCancel">
          <text class="pk-dialog__close-icon">✕</text>
        </view>
      </view>
      <view class="pk-dialog__body">
        <slot />
      </view>
      <view v-if="$slots.footer || showFooter" class="pk-dialog__footer">
        <slot name="footer">
          <PkButton v-if="showCancel" type="gray" :text="cancelText" @click="onCancel" />
          <PkButton type="gold" :text="confirmText" @click="onConfirm" />
        </slot>
      </view>
    </view>
  </view>
</template>

<script setup>
import PkButton from '../PkButton/index.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '提示' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  showCancel: { type: Boolean, default: true },
  showFooter: { type: Boolean, default: true },
  closeOnClickMask: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'confirm', 'cancel'])

function onConfirm() { emit('confirm'); emit('update:visible', false) }
function onCancel()  { emit('cancel');  emit('update:visible', false) }
function onMaskClick() {
  if (props.closeOnClickMask) onCancel()
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.pk-dialog {
  position: fixed;
  inset: 0;
  background: $color-bg-mask;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  &__panel {
    width: 880rpx;
    max-width: 90vw;
    background: $color-bg-panel;
    border: $border-width-thin solid $color-border-gold;
    border-radius: $radius-xl;
    box-shadow: $shadow-modal;
    overflow: hidden;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    padding: 32rpx;
    background: linear-gradient(180deg, rgba(230,194,90,0.12), transparent);
  }

  &__title {
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-gold;
  }

  &__close {
    position: absolute;
    right: 24rpx;
    top: 50%;
    transform: translateY(-50%);
    width: 56rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__close-icon { color: $color-text-secondary; font-size: 32rpx; }

  &__body {
    padding: 40rpx 48rpx;
    color: $color-text-regular;
    font-size: $font-size-base;
    text-align: center;
  }

  &__footer {
    display: flex;
    gap: 24rpx;
    padding: 0 48rpx 48rpx;
  }
}
</style>
