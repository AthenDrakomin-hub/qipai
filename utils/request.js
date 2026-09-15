// ============================================================
// V-POKER 网络请求层（uni.request 统一封装）
// - baseURL 统一配置，切换后端只改 requestConfig.baseURL
// - 自动注入 token：Authorization: Bearer xxx
// - 统一解包 R<T>：code=200 返回 data；401 清登录态回登录页；其余抛 Error(msg)
// - 可选全局 loading（计数合并，避免并发请求闪烁）
// ============================================================

export const requestConfig = {
  baseURL: '',                          // TODO: 接入真实后端时填写，例如 https://api.v-poker.com
  timeout: 10000,
  tokenKey: 'token',
  userInfoKey: 'userInfo',
  loginPath: '/pages/login/index',
}

export function setBaseURL(url) {
  requestConfig.baseURL = url || ''
}

let loadingCount = 0
function showLoading(title) {
  loadingCount++
  if (loadingCount === 1) uni.showLoading({ title, mask: true })
}
function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) uni.hideLoading()
}

let redirecting = false
// 登录态失效统一处理：清本地存储 + 回登录页（防重复跳转）
export function handleUnauthorized() {
  if (redirecting) return
  redirecting = true
  uni.removeStorageSync(requestConfig.tokenKey)
  uni.removeStorageSync(requestConfig.userInfoKey)
  uni.reLaunch({
    url: requestConfig.loginPath,
    complete: () => setTimeout(() => { redirecting = false }, 800),
  })
}

export function request(options = {}) {
  const {
    url = '',
    method = 'GET',
    data = {},
    header = {},
    auth = true,
    loading = false,
    loadingText = '加载中...',
  } = options

  return new Promise((resolve, reject) => {
    const token = auth ? uni.getStorageSync(requestConfig.tokenKey) : ''
    const finalHeader = { 'Content-Type': 'application/json', ...header }
    if (token) finalHeader.Authorization = `Bearer ${token}`

    if (loading) showLoading(loadingText)

    uni.request({
      url: requestConfig.baseURL + url,
      method: String(method).toUpperCase(),
      data,
      header: finalHeader,
      timeout: requestConfig.timeout,
      success: (res) => {
        const body = res.data || {}
        if (body.code === 200) {
          resolve(body.data)
          return
        }
        if (body.code === 401) {
          handleUnauthorized()
          reject(new Error(body.msg || '登录已过期，请重新登录'))
          return
        }
        reject(new Error(body.msg || `请求失败（${body.code != null ? body.code : res.statusCode}）`))
      },
      fail: (err) => {
        reject(new Error(err && err.errMsg ? err.errMsg : '网络异常，请稍后重试'))
      },
      complete: () => {
        if (loading) hideLoading()
      },
    })
  })
}

export const http = {
  get: (url, data = {}, options = {}) => request({ ...options, url, method: 'GET', data }),
  post: (url, data = {}, options = {}) => request({ ...options, url, method: 'POST', data }),
  put: (url, data = {}, options = {}) => request({ ...options, url, method: 'PUT', data }),
  del: (url, data = {}, options = {}) => request({ ...options, url, method: 'DELETE', data }),
}
