<template>
  <view class="page">
    <view class="page__header">
      <view class="page__back" @click="back">←</view>
      <text class="page__title">联系客服</text>
    </view>

    <view class="cols">
      <PkCard class="col">
        <view class="cs-head">
          <PkAvatar :size="100" name="客" />
          <view>
            <text class="cs-name">在线客服</text>
            <text class="cs-time">工作时间 9:00-24:00</text>
          </view>
        </view>
        <PkButton type="gold" text="发起对话" @click="chat" />
        <view class="cs-records">
          <text class="cs-label">投诉记录</text>
          <view v-for="r in records" :key="r.id" class="cs-row">
            <text>{{ r.time }} {{ r.title }}</text>
            <text class="cs-status">{{ r.status }}</text>
          </view>
        </view>
      </PkCard>

      <PkCard class="col">
        <text class="cs-label">问题描述</text>
        <textarea v-model="content" class="cs-input" placeholder="请详细描述您遇到的问题…" />
        <view class="cs-faq">
          <text class="cs-label">常见问题</text>
          <view v-for="f in faqs" :key="f.q" class="cs-faq-item">
            <text class="cs-q">{{ f.q }}</text>
            <text class="cs-a">{{ f.a }}</text>
          </view>
        </view>
        <PkButton type="gold" :loading="submitting" text="提交投诉" @click="submit" />
      </PkCard>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import PkCard from '@/components/PkCard/index.vue'
import PkButton from '@/components/PkButton/index.vue'
import PkAvatar from '@/components/PkAvatar/index.vue'
import { api } from '@/api/index.js'

const content = ref('')
const submitting = ref(false)
const records = ref([
  { id: 1, time: '09-10', title: '投诉客服不回复', status: '已处理' },
  { id: 2, time: '09-08', title: '投诉充值未到账', status: '已处理' },
])
const faqs = [
  { q: '如何充值？', a: '点击个人中心-充值，选择金额和支付方式' },
  { q: '如何提现？', a: '绑定收款账号后可申请提现' },
  { q: '账号被冻结怎么办？', a: '联系在线客服核实身份后解冻' },
]
function back() { uni.navigateBack() }
function chat() { uni.showToast({ title: '正在连接客服…', icon: 'none' }) }
async function submit() {
  if (!content.value) return uni.showToast({ title: '请填写问题描述', icon: 'none' })
  submitting.value = true
  try {
    await api.submitComplaint({ title: content.value.slice(0, 20), content: content.value })
    uni.showToast({ title: '已提交', icon: 'success' })
    content.value = ''
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss">
@import '@/styles/tokens/index.scss';
.page { @include page-bg; @include safe-area-padding(24rpx, 40rpx, 24rpx, 40rpx); min-height: 100vh; }
.page__header { display: flex; align-items: center; margin-bottom: 24rpx; }
.page__back { font-size: 40rpx; color: #fff; width: 60rpx; }
.page__title { flex: 1; text-align: center; font-size: $font-size-lg; font-weight: bold; color: #fff; }
.cols { display: flex; gap: 24rpx; }
.col { flex: 1; padding: 32rpx; }
.cs-head { display: flex; align-items: center; gap: 20rpx; margin-bottom: 24rpx; }
.cs-name { display: block; color: #fff; font-size: $font-size-md; font-weight: bold; }
.cs-time { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-top: 4rpx; }
.cs-records { margin-top: 32rpx; }
.cs-label { display: block; color: $color-text-secondary; font-size: $font-size-sm; margin-bottom: 12rpx; }
.cs-row { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid $color-border-light; color: #fff; font-size: $font-size-sm; }
.cs-status { color: $color-success; }
.cs-input {
  width: 100%; height: 180rpx;
  background: $color-bg-input; border-radius: $radius-base;
  padding: 20rpx; color: #fff; font-size: $font-size-base;
  margin-bottom: 24rpx;
}
.cs-faq { margin-bottom: 24rpx; }
.cs-faq-item { padding: 12rpx 0; border-bottom: 1rpx solid $color-border-light; }
.cs-q { display: block; color: #fff; font-size: $font-size-sm; }
.cs-a { display: block; color: $color-text-secondary; font-size: $font-size-xs; margin-top: 4rpx; }
</style>
