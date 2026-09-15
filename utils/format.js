// ============================================================
// V-POKER 格式化工具（全局唯一实现，页面禁止再复制粘贴）
// ============================================================

// 通用数字展示：≥1万 显示 x.x万，否则千分位（余额、底池等）
export function formatNumber(n) {
  const num = Number(n)
  if (n === null || n === undefined || n === '' || Number.isNaN(num)) return '0'
  if (Math.abs(num) >= 10000) return (num / 10000).toFixed(1) + '万'
  return num.toLocaleString()
}

// 金额展示：完整千分位，可选带正负号（结算、流水、输赢金额）
export function formatAmount(n, { sign = false } = {}) {
  const num = Number(n)
  if (Number.isNaN(num)) return '0'
  const text = Math.abs(num).toLocaleString()
  if (!sign) return (num < 0 ? '-' : '') + text
  return (num > 0 ? '+' : num < 0 ? '-' : '') + text
}
