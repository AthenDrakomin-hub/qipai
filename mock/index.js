// ============================================================
// V-POKER Mock 数据层（严格对齐《接口文档 v1.0》）
// 统一返回 R<T>：{ code, msg, data, timestamp }
// ============================================================

const now = () => Date.now()
const ok = (data) => ({ code: 200, msg: 'success', data, timestamp: now() })
const fail = (code = 500, msg = '业务异常', data = null) => ({ code, msg, data, timestamp: now() })

// ---------- 用户 ----------
export const mockMe = {
  id: 10086,
  username: 'player1',
  nickname: '清风明月',
  role: 2,                       // 二级代理（可开房）
  roleDesc: '二级代理',
  credits: 25860,
  inviteCode: 'V8K9M2',
  parentId: 10001,
  parentInviteCode: 'ADMIN',
  avatar: '',
  hasFeeFailure: false,
  status: 0,
  lastLoginTime: '2026-09-14 14:30:00',
}

// ---------- 游戏类型（静态字典） ----------
export const GAME_TYPE = {
  TEXAS:         { code: 'TEXAS',         desc: '德州竞技', icon: '♠️' },
  JINHUA:        { code: 'JINHUA',        desc: '金花竞技', icon: '🃏' },
  SANGONG:       { code: 'SANGONG',       desc: '抢庄三公', icon: '🎴' },
  DOUNIU:        { code: 'DOUNIU',        desc: '抢庄牛牛', icon: '🐂' },
  TONGBI_NIUNIU: { code: 'TONGBI_NIUNIU', desc: '通比牛牛', icon: '🐃' },
  TONGBI_SANGONG:{ code: 'TONGBI_SANGONG',desc: '通比三公', icon: '🎲' },
}

export const ROOM_LEVEL = {
  PRIMARY:   { code: 'PRIMARY',   desc: '初级房', minBuyin: 200,  maxBuyin: 1000,  threshold: 100 },
  ADVANCED:  { code: 'ADVANCED',  desc: '高级房', minBuyin: 500,  maxBuyin: 5000,  threshold: 1000 },
  PREMIUM:   { code: 'PREMIUM',   desc: '顶级房', minBuyin: 1000, maxBuyin: 20000, threshold: 3000 },
}

// ---------- 大厅房间列表 ----------
export function mockLobby({ gameType, roomLevel } = {}) {
  const rooms = [
    { id: 101, roomNo: '880123', roomName: '德州-初级-A桌', gameType: 'TEXAS', roomLevel: 'PRIMARY',
      status: 1, ownerId: 10001, ownerNickname: '代理老王', hasPassword: false,
      minBuyin: 200, maxBuyin: 1000, maxPlayers: 8, currentPlayers: 6, createTime: '2026-09-14 10:00:00' },
    { id: 102, roomNo: '880321', roomName: '德州-初级-B桌', gameType: 'TEXAS', roomLevel: 'PRIMARY',
      status: 0, ownerId: 10002, ownerNickname: '代理老张', hasPassword: true,
      minBuyin: 200, maxBuyin: 1000, maxPlayers: 8, currentPlayers: 2, createTime: '2026-09-14 11:00:00' },
    { id: 201, roomNo: '660456', roomName: '金花-高级', gameType: 'JINHUA', roomLevel: 'ADVANCED',
      status: 1, ownerId: 10001, ownerNickname: '代理老王', hasPassword: false,
      minBuyin: 500, maxBuyin: 5000, maxPlayers: 6, currentPlayers: 5, createTime: '2026-09-14 09:00:00' },
    { id: 301, roomNo: '770789', roomName: '三公-顶级', gameType: 'SANGONG', roomLevel: 'PREMIUM',
      status: 0, ownerId: 10003, ownerNickname: '代理阿强', hasPassword: false,
      minBuyin: 1000, maxBuyin: 20000, maxPlayers: 5, currentPlayers: 1, createTime: '2026-09-14 13:00:00' },
    { id: 401, roomNo: '550321', roomName: '牛牛-初级', gameType: 'DOUNIU', roomLevel: 'PRIMARY',
      status: 1, ownerId: 10002, ownerNickname: '代理老张', hasPassword: false,
      minBuyin: 200, maxBuyin: 1000, maxPlayers: 5, currentPlayers: 4, createTime: '2026-09-14 12:00:00' },
  ]
  let out = rooms.filter(r => r.status !== 2 && r.status !== 3)
  if (gameType) out = out.filter(r => r.gameType === gameType)
  if (roomLevel) out = out.filter(r => r.roomLevel === roomLevel)
  return out
}

// ---------- 房间详情（含 players） ----------
export function mockRoomDetail(roomId = 101) {
  const room = {
    id: roomId, roomNo: '880123', gameType: 'TEXAS', roomLevel: 'PRIMARY',
    roomName: '德州-初级-A桌', ownerId: 10001, ownerNickname: '代理老王',
    maxPlayers: 8, minBuyin: 200, maxBuyin: 1000, status: 1,
    currentRound: 3, totalRounds: 25,
    totalTurnover: 4200, totalRake: 126, totalWaterFee: 42,
    fixedBetAmount: 0, isPublicMatch: false, createTime: '2026-09-14 10:00:00',
  }
  const players = [
    { userId: 31, username: 'player31', nickname: '老张', seatNo: 0, isObserver: false,
      buyinCredits: 500, currentCredits: 620, totalProfit: 120, isAutoPlay: false, isLooked: false },
    { userId: 32, username: 'player32', nickname: '小王', seatNo: 1, isObserver: false,
      buyinCredits: 800, currentCredits: 1200, totalProfit: 400, isAutoPlay: false, isLooked: false },
    { userId: 33, username: 'player33', nickname: '莉莉', seatNo: 2, isObserver: false,
      buyinCredits: 500, currentCredits: 300, totalProfit: -200, isAutoPlay: false, isLooked: false },
    { userId: 34, username: 'player34', nickname: '阿强', seatNo: 3, isObserver: false,
      buyinCredits: 500, currentCredits: 480, totalProfit: -20, isAutoPlay: false, isLooked: false },
  ]
  return { room, players }
}

// ---------- 我的战绩（分页） ----------
export function mockGameHistory(page = 1, size = 10) {
  const list = [
    { id: 1, roomNo: '880123', gameType: 'TEXAS',         roundNo: 8,  turnover: 1286000, rake: 38580, win: true,  profit: 385800, createTime: '2026-09-14 14:30:00' },
    { id: 2, roomNo: '660456', gameType: 'JINHUA',        roundNo: 5,  turnover: 86000,   rake: 2580,  win: false, profit: -12900, createTime: '2026-09-08 20:15:00' },
    { id: 3, roomNo: '770789', gameType: 'SANGONG',       roundNo: 12, turnover: 256000,  rake: 7680,  win: true,  profit: 76800,  createTime: '2026-09-07 16:45:00' },
    { id: 4, roomNo: '550321', gameType: 'DOUNIU',        roundNo: 3,  turnover: 45000,   rake: 1350,  win: false, profit: -6750,  createTime: '2026-09-06 11:20:00' },
    { id: 5, roomNo: '550555', gameType: 'TONGBI_NIUNIU', roundNo: 18, turnover: 98000,   rake: 2940,  win: true,  profit: 29400,  createTime: '2026-09-05 09:30:00' },
  ]
  return { records: list, total: list.length, size, current: page, pages: 1 }
}

// ---------- 资金流水（分页） ----------
export function mockCreditLog(page = 1, size = 20) {
  const list = [
    { id: 1, changeType: 1,  changeValue: 60000, beforeValue: 25860, afterValue: 85860, roomNo: '', remark: '6元充值', createTime: '2026-09-14 14:00:00' },
    { id: 2, changeType: 5,  changeValue: -5000, beforeValue: 30860, afterValue: 25860, roomNo: '', remark: '水费扣除', createTime: '2026-09-10 13:30:00' },
    { id: 3, changeType: 3,  changeValue: 58600, beforeValue: 30860, afterValue: 89460, roomNo: '880123', remark: '对局赢', createTime: '2026-09-10 12:00:00' },
    { id: 4, changeType: 3,  changeValue: -12500,beforeValue: 27740, afterValue: 15240, roomNo: '660456', remark: '对局输', createTime: '2026-09-09 20:00:00' },
    { id: 5, changeType: 11, changeValue: 3200,  beforeValue: 37040, afterValue: 40240, roomNo: '', remark: '代理返佣', createTime: '2026-09-09 10:00:00' },
    { id: 6, changeType: 12, changeValue: -2000,  beforeValue: 39040, afterValue: 37040, roomNo: '', remark: '玩家赠送', createTime: '2026-09-08 18:00:00' },
  ]
  return { records: list, total: list.length, size, current: page, pages: 1 }
}

// ---------- 我创建的房间 ----------
export function mockMyOwned() {
  return [
    { id: 101, roomNo: '880123', gameType: 'TEXAS', roomLevel: 'PRIMARY', status: 1,
      currentRound: 8, totalRounds: 25, maxPlayers: 8, currentPlayers: 6 },
    { id: 201, roomNo: '660456', gameType: 'JINHUA', roomLevel: 'ADVANCED', status: 1,
      currentRound: 5, totalRounds: 15, maxPlayers: 6, currentPlayers: 5 },
    { id: 301, roomNo: '770789', gameType: 'SANGONG', roomLevel: 'PREMIUM', status: 1,
      currentRound: 12, totalRounds: 20, maxPlayers: 5, currentPlayers: 4 },
  ]
}

// ---------- 代理下线 ----------
export function mockSubordinates() {
  return [
    { id: 31, username: 'player31', nickname: '老张', role: 1, credits: 19000, inviteCode: 'P31XXX', createTime: '2026-09-01 10:00:00', totalTurnover: 120000, roomCount: 3 },
    { id: 32, username: 'player32', nickname: '小王', role: 1, credits: 32000, inviteCode: 'P32XXX', createTime: '2026-09-02 11:00:00', totalTurnover: 86000,  roomCount: 2 },
    { id: 33, username: 'player33', nickname: '阿强', role: 1, credits: 6300,  inviteCode: 'P33XXX', createTime: '2026-09-03 14:00:00', totalTurnover: 45000,  roomCount: 1 },
  ]
}

// ---------- 牌桌一局 ----------
export function mockPlayRound(roomId) {
  return {
    round: 4,
    roundId: 9001,
    gameType: 'TEXAS',
    bankerId: null,
    dice: [],
    cards: {
      holeCards: { '10086': [{ suit: 'heart', value: '7' }, { suit: 'diamond', value: '9' }] },
      cardTypes: {},
    },
    bets: { '31': 200, '32': 200, '10086': 200 },
    profits: {},
    roundTurnover: 800,
    roundRake: 24,
    finalSettlement: null,
  }
}

export { ok, fail }
