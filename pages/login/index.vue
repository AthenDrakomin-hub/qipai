<template>
  <view class="login">
    <view class="login__left">
      <view class="login__brand">
        <view class="login__vmark">V</view>
        <text class="login__name">V-POKER</text>
      </view>

      <view class="login__form">
        <PkInput v-model="form.account" placeholder="请输入账号">
          <template #icon><text>👤</text></template>
        </PkInput>
        <PkInput v-model="form.password" type="password" placeholder="请输入密码" style="margin-top: 24rpx">
          <template #icon><text>🔒</text></template>
        </PkInput>

        <view class="login__row">
          <text class="login__link" @click="onForgot">忘记密码？</text>
          <text class="login__link" @click="onGoRegister">注册账号</text>
        </view>

        <PkButton type="gold" size="lg" block :text="'登 录'" :loading="loading" @click="onLogin" style="margin-top: 32rpx" />
      </view>

      <view class="login__oauth">
        <PkButton type="purple" size="sm" text="游客登录" />
        <PkButton type="green" size="sm" text="微信登录" />
        <PkButton type="gold" size="sm" text="手机登录" />
      </view>

      <text class="login__agreement">我已阅读并同意《用户协议》《隐私政策》</text>
    </view>

    <view class="login__right">
      <view class="login__dealer" />
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import PkInput from '@/components/PkInput/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import { api } from '@/api/index.js'

const form = reactive({ account: '', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.account || !form.password) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await api.login(form.account.trim(), form.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/hall/index' }), 400)
  } catch (e) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function onForgot() { uni.showToast({ title: '请联系客服重置密码', icon: 'none' }) }
function onGoRegister() { uni.navigateTo({ url: '/pages/register/index' }) }
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';

.login {
  @include page-bg;
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 80rpx;
  gap: 80rpx;

  &__left { flex: 1; max-width: 560rpx; }

  &__brand { display: flex; align-items: center; gap: 20rpx; margin-bottom: 48rpx; }
  &__vmark {
    width: 80rpx; height: 80rpx;
    border: 4rpx solid $color-brand-gold;
    border-radius: 16rpx;
    color: $color-brand-gold;
    font-size: 48rpx; font-weight: 900;
    display: flex; align-items: center; justify-content: center;
  }
  &__name { font-size: $font-size-xl; font-weight: 900; color: $color-text-gold; letter-spacing: 4rpx; }

  &__row {
    display: flex; justify-content: space-between;
    margin-top: 20rpx;
  }
  &__link { color: $color-text-secondary; font-size: $font-size-sm; }

  &__oauth {
    display: flex; gap: 20rpx; margin-top: 40rpx;
  }

  &__agreement {
    display: block; margin-top: 24rpx;
    color: $color-text-placeholder; font-size: $font-size-xs;
  }

  &__right {
    flex: 1;
    height: 100%;
    display: flex; align-items: center; justify-content: center;
  }
  &__dealer {
    width: 420rpx; height: 640rpx;
    background: linear-gradient(180deg, rgba(230,194,90,0.15), transparent);
    border-radius: $radius-2xl;
  }
}
</style>
