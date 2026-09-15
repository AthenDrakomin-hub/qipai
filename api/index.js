// ============================================================
// V-POKER API 门面层（对齐《接口文档 v1.0》路径与字段）
// - USE_MOCK=true ：前端独立联调，数据来自 mock/index.js
// - USE_MOCK=false：走 utils/request.js 真实后端
// - 切换后端：1) 将 USE_MOCK 改为 false  2) 在 utils/request.js 配置 baseURL
// - 统一契约：所有方法返回「已解包的 data」，失败抛 Error(msg)
// ============================================================

import * as M from '@/mock/index.js'
import { request } from '@/utils/request.js'

const USE_MOCK = true

const delay = (ms = 200) => new Promise(r => setTimeout(r, ms))

// 统一解包 R<T>：code=200 返回 data，401 走登录态失效处理，其余抛错
function unwrap(res) {
  if (res.code === 200) return res.data
  if (res.code === 401) {
    // 与真实请求层保持一致的兜底处理
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    uni.reLaunch({ url: '/pages/login/index' })
    throw new Error(res.msg || '登录已过期，请重新登录')
  }
  throw new Error(res.msg || '请求失败')
}

// ---------- mock 实现（模拟后端：含参数校验/延迟/假数据） ----------
const mockApi = {
  async health() {
    await delay(100)
    return unwrap(M.ok('ok'))
  },

  async login(username, password) {
    await delay(600)
    if (!username || !password) throw new Error('账号或密码不能为空')
    return {
      token: 'mock-jwt-' + Date.now(),
      userId: M.mockMe.id,
      username,
      nickname: M.mockMe.nickname,
      role: M.mockMe.role,
      roleDesc: M.mockMe.roleDesc,
      credits: M.mockMe.credits,
      inviteCode: M.mockMe.inviteCode,
      parentInviteCode: M.mockMe.parentInviteCode,
      hasFeeFailure: M.mockMe.hasFeeFailure,
      avatar: M.mockMe.avatar,
    }
  },

  async register(payload) {
    await delay(700)
    if (!payload.username || payload.username.length < 4) throw new Error('账号长度4-20位')
    if (!payload.password || payload.password.length < 6) throw new Error('密码长度6-20位')
    if (payload.password !== payload.confirmPassword) throw new Error('两次输入的密码不一致')
    if (!payload.inviteCode || payload.inviteCode.length !== 6) throw new Error('邀请码必须为6位')
    if (!payload.securityCode) throw new Error('安全码长度4-20位')
    if (payload.password === payload.securityCode) throw new Error('安全码不能与登录密码相同')
    return null
  },

  async logout() {
    await delay(100)
  },

  async me() {
    await delay(150)
    return { ...M.mockMe }
  },

  async updateProfile(payload) {
    await delay(200)
    const u = uni.getStorageSync('userInfo') || { ...M.mockMe }
    return { ...u, ...payload }
  },

  async gameHistory(page = 1, size = 10) {
    await delay(200)
    return M.mockGameHistory(page, size)
  },

  async creditLog(page = 1, size = 20) {
    await delay(200)
    return M.mockCreditLog(page, size)
  },

  async lobby(params = {}) {
    await delay(250)
    return M.mockLobby(params)
  },

  async roomDetail(roomId) {
    await delay(200)
    return M.mockRoomDetail(roomId)
  },

  async joinRoom(payload) {
    await delay(500)
    if (!payload.roomNo) throw new Error('房间号不能为空')
    if (!payload.buyin || Number(payload.buyin) <= 0) throw new Error('请选择带入游戏币')
    if (Number(payload.buyin) > M.mockMe.credits) throw new Error('余额不足')
    if (payload.password && payload.password !== '123456') throw new Error('房间密码错误')
    return { room: { id: 101, roomNo: payload.roomNo }, me: null, players: [] }
  },

  async createRoom(payload) {
    await delay(600)
    if (!payload.gameType) throw new Error('请选择游戏类型')
    if (!payload.roomLevel) throw new Error('请选择房间等级')
    if (!payload.initChip || Number(payload.initChip) <= 0) throw new Error('初始筹码无效')
    if (Number(payload.initChip) > M.mockMe.credits) throw new Error('余额不足，无法创建房间')
    return { id: 999, roomNo: '888888' }
  },

  async myOwnedRooms() {
    await delay(200)
    return M.mockMyOwned()
  },

  async closeRoom(roomId) {
    await delay(200)
    if (!roomId) throw new Error('房间不存在')
    return null
  },

  async playRound(roomId) {
    await delay(400)
    return M.mockPlayRound(roomId)
  },

  async subordinates() {
    await delay(250)
    return M.mockSubordinates()
  },

  async gift(payload) {
    await delay(400)
    if (!payload.targetUserId) throw new Error('请选择玩家')
    if (!payload.changeValue || Number(payload.changeValue) <= 0) throw new Error('赠送数量无效')
    if (Number(payload.changeValue) > M.mockMe.credits) throw new Error('余额不足')
    return M.mockMe.credits - Number(payload.changeValue)
  },

  async submitComplaint(payload) {
    await delay(500)
    if (!payload.title || !payload.content) throw new Error('请填写投诉标题和内容')
    return null
  },
}

// ---------- 真实后端实现（USE_MOCK=false 时生效） ----------
const realApi = {
  health: () => request({ url: '/health', auth: false }),
  login: (username, password) => request({ url: '/auth/login', method: 'POST', data: { username, password }, auth: false }),
  register: (payload) => request({ url: '/auth/register', method: 'POST', data: payload, auth: false }),
  logout: () => request({ url: '/auth/logout', method: 'POST' }),
  me: () => request({ url: '/user/me' }),
  updateProfile: (payload) => request({ url: '/user/profile', method: 'PUT', data: payload }),
  gameHistory: (page = 1, size = 10) => request({ url: `/user/game-history?page=${page}&size=${size}` }),
  creditLog: (page = 1, size = 20) => request({ url: `/user/credit-log?page=${page}&size=${size}` }),
  lobby: (params = {}) => request({ url: '/room/lobby', data: params }),
  roomDetail: (roomId) => request({ url: `/room/detail?roomId=${roomId}` }),
  joinRoom: (payload) => request({ url: '/room/join', method: 'POST', data: payload }),
  createRoom: (payload) => request({ url: '/room/create', method: 'POST', data: payload }),
  myOwnedRooms: () => request({ url: '/room/my-owned' }),
  closeRoom: (roomId) => request({ url: '/room/close', method: 'POST', data: { roomId } }),
  playRound: (roomId) => request({ url: '/game/play-round', method: 'POST', data: { roomId } }),
  subordinates: () => request({ url: '/agent/subordinates' }),
  gift: (payload) => request({ url: '/agent/gift', method: 'POST', data: payload }),
  submitComplaint: (payload) => request({ url: '/complaint/submit', method: 'POST', data: payload }),
}

function call(name, ...args) {
  const impl = USE_MOCK ? mockApi : realApi
  return impl[name](...args)
}

// ---------- 统一门面（登录态存储等本地副作用集中在此） ----------
export const api = {
  // ===== 鉴权 =====
  health: () => call('health'),

  login: async (username, password) => {
    const data = await call('login', username, password)
    uni.setStorageSync('token', data.token)
    uni.setStorageSync('userInfo', data)
    return data
  },

  register: (payload) => call('register', payload),

  logout: async () => {
    try {
      await call('logout')
    } finally {
      uni.removeStorageSync('token')
      uni.removeStorageSync('userInfo')
    }
  },

  // ===== 用户 =====
  me: () => call('me'),

  updateProfile: async (payload) => {
    const next = await call('updateProfile', payload)
    uni.setStorageSync('userInfo', next)
    return next
  },

  gameHistory: (page = 1, size = 10) => call('gameHistory', page, size),

  creditLog: (page = 1, size = 20) => call('creditLog', page, size),

  // ===== 房间 =====
  lobby: (params = {}) => call('lobby', params),

  roomDetail: (roomId) => call('roomDetail', roomId),

  joinRoom: (payload) => call('joinRoom', payload),

  createRoom: (payload) => call('createRoom', payload),

  myOwnedRooms: () => call('myOwnedRooms'),

  closeRoom: (roomId) => call('closeRoom', roomId),

  playRound: (roomId) => call('playRound', roomId),

  // ===== 代理 =====
  subordinates: () => call('subordinates'),

  gift: (payload) => call('gift', payload),

  // ===== 投诉 =====
  submitComplaint: (payload) => call('submitComplaint', payload),
}
