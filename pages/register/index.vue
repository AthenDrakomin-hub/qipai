<template>
  <view class="register">
    <view class="register__back" @click="onBack">
      <text>←</text>
    </view>

    <view class="register__panel">
      <text class="register__title">创建账号</text>

      <view class="register__form">
        <PkInput v-model="form.account" placeholder="请输入账号">
          <template #icon><text>👤</text></template>
        </PkInput>
        <PkInput v-model="form.password" type="password" placeholder="请设置密码" style="margin-top: 20rpx">
          <template #icon><text>🔒</text></template>
        </PkInput>
        <PkInput v-model="form.confirm" type="password" placeholder="请确认密码" style="margin-top: 20rpx">
          <template #icon><text>🔒</text></template>
        </PkInput>
        <PkInput v-model="form.inviteCode" placeholder="请输入邀请码" style="margin-top: 20rpx">
          <template #icon><text>🎁</text></template>
        </PkInput>
        <PkInput v-model="form.safeCode" placeholder="请输入安全码" style="margin-top: 20rpx">
          <template #icon><text>🛡️</text></template>
        </PkInput>
      </view>

      <view class="register__row">
        <text class="register__tip">已有账号？</text>
        <text class="register__link" @click="onBack">立即登录</text>
      </view>

      <PkButton type="gold" size="lg" block :text="'注 册'" :loading="loading" @click="onRegister" style="margin-top: 24rpx" />

      <view class="register__agree">
        <view class="register__checkbox" :class="{ checked: agreed }" @click="agreed = !agreed">
          <text v-if="agreed">✓</text>
        </view>
        <text class="register__agree-text">我已阅读并同意《用户协议》《隐私政策》</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import PkInput from '@/components/PkInput/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { api } from '@/api/index.js'

const form = reactive({ account: '', password: '', confirm: '', inviteCode: '', safeCode: '' })
const agreed = ref(false)
const loading = ref(false)

async function onRegister() {
  if (!form.account || !form.password) return uni.showToast({ title: '请填写账号密码', icon: 'none' })
  if (form.password !== form.confirm) return uni.showToast({ title: '两次密码不一致', icon: 'none' })
  if (!form.inviteCode) return uni.showToast({ title: '邀请码必填', icon: 'none' })
  if (!form.safeCode) return uni.showToast({ title: '安全码必填', icon: 'none' })
  if (!agreed.value) return uni.showToast({ title: '请先勾选协议', icon: 'none' })

  loading.value = true
  try {
    await api.register({
      username: form.account.trim(),
      password: form.password,
      confirmPassword: form.confirm,
      inviteCode: form.inviteCode.trim(),
      securityCode: form.safeCode.trim(),
    })
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    uni.showToast({ title: e.message || '注册失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function onBack() { uni.navigateBack() }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.register {
  @include page-bg;
  width: 100%; min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  position: relative;

  &__back {
    position: absolute; top: 32rpx; left: 40rpx;
    width: 64rpx; height: 64rpx;
    display: flex; align-items: center; justify-content: center;
    color: #fff; font-size: 40rpx;
  }

  &__panel {
    width: 640rpx;
    @include card;
    padding: 56rpx;
  }

  &__title {
    display: block; text-align: center;
    font-size: $font-size-xl;
    font-weight: bold; color: $color-text-gold;
    margin-bottom: 40rpx;
  }

  &__row {
    display: flex; justify-content: center; gap: 12rpx;
    margin-top: 20rpx;
  }
  &__tip { color: $color-text-secondary; font-size: $font-size-sm; }
  &__link { color: $color-brand-purple-light; font-size: $font-size-sm; }

  &__agree {
    display: flex; align-items: center; gap: 12rpx;
    margin-top: 24rpx;
  }
  &__checkbox {
    width: 28rpx; height: 28rpx;
    border: 2rpx solid $color-text-secondary;
    border-radius: 6rpx;
    display: flex; align-items: center; justify-content: center;
    color: #fff; font-size: 20rpx;
    &.checked { background: $color-brand-gold; border-color: $color-brand-gold; }
  }
  &__agree-text { color: $color-text-placeholder; font-size: $font-size-xs; }
}
</style>
