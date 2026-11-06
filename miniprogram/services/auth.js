const api = require('./api')

const SESSION_KEY = 'volt.session'
const LOGOUT_KEY = 'volt.loggedOut'
const TOKEN_MAX_AGE = 29 * 24 * 60 * 60 * 1000
let autoLogin = null
let autoLoginDisabled = false

function saveSession(session) {
  wx.setStorageSync(SESSION_KEY, session)
  return session
}

function isLoggedOut() {
  try {
    return Boolean(wx.getStorageSync(LOGOUT_KEY))
  } catch (error) {
    return false
  }
}

function clearLogoutMark() {
  try {
    wx.removeStorageSync(LOGOUT_KEY)
  } catch (error) {
    // ignore storage failures
  }
}

function getSession() {
  return wx.getStorageSync(SESSION_KEY) || null
}

function getUser() {
  if (isLoggedOut()) return null
  const session = getSession()
  return session && session.user ? session.user : null
}

function isLoggedIn() {
  return !isLoggedOut() && !!getSession()
}

function isSessionFresh(session) {
  if (!session || !session.token) return false
  if (!session.createdAt) return true
  return Date.now() - session.createdAt < TOKEN_MAX_AGE
}

function clearSession() {
  try {
    wx.removeStorageSync(SESSION_KEY)
  } catch (error) {
    // ignore storage failures
  }
}

function bootstrap() {
  if (isLoggedOut()) {
    clearSession()
    autoLoginDisabled = true
    api.setAuthGate(Promise.resolve(null))
    return Promise.resolve(null)
  }
  const existing = getSession()
  if (existing && existing.mode === 'demo') clearSession()
  const session = getSession()
  if (isSessionFresh(session)) {
    api.setAuthGate(Promise.resolve(session))
    return Promise.resolve(session)
  }
  if (session) clearSession()
  const login = startAutoLogin()
  api.setAuthGate(login)
  return login.catch(() => null)
}

function startAutoLogin() {
  if (autoLogin) return autoLogin
  autoLogin = loginWithWechat(null).catch((error) => {
    autoLogin = null
    api.setAuthGate(null)
    throw error
  })
  return autoLogin
}

function cleanProfile(profile) {
  if (!profile) return null
  const next = { ...profile }
  // chooseAvatar 返回的是临时文件路径，后端无法长期访问；没有上传接口时不落库。
  if (next.avatarUrl && !/^https?:\/\//.test(next.avatarUrl)) {
    delete next.avatarUrl
  }
  return next
}

function loginWithWechat(profile) {
  autoLoginDisabled = false
  const safeProfile = cleanProfile(profile)
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (!res.code) {
          reject(new api.ApiError('WX_LOGIN_FAILED', '微信授权失败，请重试'))
          return
        }
        api.auth
          .login({ code: res.code, profile: safeProfile })
          .then((result) => {
            clearLogoutMark()
            const session = saveSession({
              token: result.token,
              mode: 'wechat',
              code: res.code,
              createdAt: Date.now(),
              user: result.member,
            })
            autoLogin = null
            api.setAuthGate(Promise.resolve(session))
            if (safeProfile && (safeProfile.nickname || safeProfile.avatarUrl)) {
              api.mine
                .updateProfile(safeProfile)
                .then((member) => {
                  session.user = member
                  saveSession(session)
                })
                .catch(() => {})
            }
            resolve(session)
          })
          .catch(reject)
      },
      fail() {
        reject(new api.ApiError('WX_LOGIN_FAILED', '微信授权失败，请检查网络'))
      },
    })
  })
}

function refreshUser(member) {
  if (isLoggedOut()) return null
  const session = getSession()
  if (!session) return null
  session.user = member
  return saveSession(session)
}

function applyMember(member, token) {
  if (isLoggedOut()) return null
  const session = getSession()
  if (!session) return null
  if (token) session.token = token
  session.user = member
  return saveSession(session)
}

function applyLoginSession(result) {
  clearLogoutMark()
  const session = saveSession({
    token: result.token,
    mode: 'phone',
    createdAt: Date.now(),
    user: result.member,
  })
  autoLogin = null
  autoLoginDisabled = false
  api.setAuthGate(Promise.resolve(session))
  return session
}

function logout() {
  clearSession()
  try {
    wx.setStorageSync(LOGOUT_KEY, true)
    const info = wx.getStorageInfoSync()
    ;(info.keys || []).forEach((key) => {
      if (key.indexOf('volt.idem.') === 0) wx.removeStorageSync(key)
    })
    wx.removeStorageSync('volt.purchasePromptShown')
  } catch (error) {
    // storage may be unavailable; the in-memory gate still closes
  }
  autoLogin = null
  autoLoginDisabled = true
  api.setAuthGate(Promise.resolve(null))
}

api.setUnauthorizedHandler(() => {
  if (autoLoginDisabled || isLoggedOut()) {
    return Promise.reject(new api.ApiError('LOGIN_REQUIRED', '请先微信登录'))
  }
  return startAutoLogin()
})

module.exports = {
  SESSION_KEY,
  bootstrap,
  getSession,
  getUser,
  isLoggedIn,
  loginWithWechat,
  refreshUser,
  applyMember,
  applyLoginSession,
  logout,
}
