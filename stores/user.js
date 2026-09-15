// ============================================================
// V-POKER 用户全局状态（轻量响应式 store，API 风格对齐 Pinia）
// - 说明：HBuilderX 项目内置 Vue，本实现零依赖即可获得 Pinia 同等能力；
//   如后续改用 Pinia，仅需将本文件替换为 defineStore 实现，页面调用方式不变。
// - 页面用法：const userStore = useUserStore()
//   userStore.credits / userStore.isAgent / await userStore.fetchMe()
// ============================================================

import { reactive, computed } from 'vue'
import { api } from '@/api/index.js'

const AGENT_ROLES = [2, 3, 6]

const state = reactive({
  info: uni.getStorageSync('userInfo') || null,
  loading: false,
  loaded: false,
})

const credits = computed(() => Number(state.info && state.info.credits) || 0)
const nickname = computed(() => (state.info && state.info.nickname) || '')
const avatar = computed(() => (state.info && state.info.avatar) || '')
const roleDesc = computed(() => (state.info && state.info.roleDesc) || '')
const isAgent = computed(() => AGENT_ROLES.includes(state.info && state.info.role))

// 拉取当前用户信息（默认命中缓存，force=true 强制刷新）
async function fetchMe(force = false) {
  if (state.loading) return state.info
  if (state.loaded && !force) return state.info
  state.loading = true
  try {
    const me = await api.me()
    setUser(me)
    return me
  } finally {
    state.loading = false
  }
}

function setUser(info) {
  state.info = info || null
  state.loaded = true
  if (info) uni.setStorageSync('userInfo', info)
}

// 本地更新余额（充值/赠送/对局结算后调用，避免整页刷新）
function setCredits(value) {
  if (!state.info) return
  state.info.credits = Number(value) || 0
  uni.setStorageSync('userInfo', state.info)
}

function isLogin() {
  return !!uni.getStorageSync('token')
}

async function logout() {
  await api.logout()
  state.info = null
  state.loaded = false
}

export function useUserStore() {
  return reactive({
    state,
    credits,
    nickname,
    avatar,
    roleDesc,
    isAgent,
    fetchMe,
    setUser,
    setCredits,
    isLogin,
    logout,
  })
}
