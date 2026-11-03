const config = require('../config/index')
const { ApiError } = require('./api-error')

let authGate = null
let unauthorizedHandler = null

function setAuthGate(promise) {
  authGate = promise || null
}

function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler || null
}

function defaultCode(statusCode) {
  if (statusCode === 401) return 'UNAUTHORIZED'
  if (statusCode === 403) return 'FORBIDDEN'
  if (statusCode === 404) return 'NOT_FOUND'
  if (statusCode === 409) return 'CONFLICT'
  if (statusCode === 429) return 'RATE_LIMITED'
  if (statusCode >= 500) return 'SERVER_ERROR'
  return 'HTTP_ERROR'
}

function readToken() {
  try {
    const session = wx.getStorageSync('volt.session')
    return (session && session.token) || ''
  } catch (error) {
    return ''
  }
}

function clearSession() {
  try {
    wx.removeStorageSync('volt.session')
  } catch (error) {
    // ignore storage failures
  }
  authGate = null
}

function request(path, options, retried) {
  const opts = options || {}
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${config.API_BASE}${path}`,
      method: opts.method || 'GET',
      data: opts.data || {},
      timeout: 15000,
      header: Object.assign(
        { 'content-type': 'application/json' },
        opts.idempotencyKey ? { 'Idempotency-Key': opts.idempotencyKey } : {},
        opts.token || readToken()
          ? { Authorization: `Bearer ${opts.token || readToken()}` }
          : {},
      ),
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data === '' ? null : res.data)
          return
        }
        const body = res.data && typeof res.data === 'object' ? res.data : {}
        const error = new ApiError(
          body.code || defaultCode(res.statusCode),
          body.message || '服务暂时不可用',
        )
        error.status = res.statusCode
        error.fields = body.fields || null
        error.requestId = body.requestId || ''

        if (res.statusCode === 401) {
          clearSession()
          if (!opts.skipAuthGate && !retried && unauthorizedHandler) {
            unauthorizedHandler()
              .then(() => request(path, opts, true))
              .then(resolve)
              .catch(() => reject(error))
            return
          }
        }
        reject(error)
      },
      fail() {
        reject(new ApiError('NETWORK_ERROR', '网络连接失败，请稍后再试'))
      },
    })
  })
}

function call(path, options) {
  if (options && options.skipAuthGate) return request(path, options)
  return (authGate || Promise.resolve())
    .catch(() => null)
    .then(() => request(path, options))
}

const IDEMPOTENCY_TTL = 10 * 60 * 1000

// 同一次操作在 10 分钟内复用同一个键：网络超时重试时后端直接回放首次结果，
// 不会重复锁位/扣次；成功后立即清掉，下一次操作换新键。
function idempotencyStorageKey(scope, identity) {
  return `volt.idem.${scope}.${identity}`
}

function readIdempotencyKey(scope, identity) {
  const storageKey = idempotencyStorageKey(scope, identity)
  try {
    const cached = wx.getStorageSync(storageKey)
    if (cached && cached.key && Date.now() - cached.at < IDEMPOTENCY_TTL) {
      return cached.key
    }
  } catch (error) {
    // ignore storage failures
  }
  const key = `${scope}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  try {
    wx.setStorageSync(storageKey, { key, at: Date.now() })
  } catch (error) {
    // ignore storage failures
  }
  return key
}

function clearIdempotencyKey(scope, identity) {
  try {
    wx.removeStorageSync(idempotencyStorageKey(scope, identity))
  } catch (error) {
    // ignore storage failures
  }
}

function callIdempotent(scope, identity, path, options) {
  const key = readIdempotencyKey(scope, identity)
  return call(path, Object.assign({}, options, { idempotencyKey: key })).then((res) => {
    clearIdempotencyKey(scope, identity)
    return res
  })
}

function todayKey() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

module.exports = {
  ApiError,
  setAuthGate,
  setUnauthorizedHandler,

  auth: {
    login(payload) {
      return call('/auth/wx/login', {
        method: 'POST',
        data: payload,
        skipAuthGate: true,
      })
    },
    linkPhone(payload) {
      return call('/auth/link-phone', {
        method: 'POST',
        data: payload,
      })
    },
  },

  catalog: {
    venues() {
      return call('/catalog/venues')
    },
    programs() {
      return call('/catalog/programs')
    },
    plans() {
      return call('/catalog/plans')
    },
    coaches(filters) {
      return call('/catalog/coaches', { data: filters })
    },
    sessions(filters) {
      return call('/catalog/sessions', { data: filters })
    },
  },

  home: {
    get() {
      return call('/member/home')
    },
  },

  schedule: {
    get(filters) {
      return call('/catalog/schedule', { data: filters })
    },
    detail(sessionId) {
      return call(`/sessions/${sessionId}`)
    },
  },

  bookings: {
    create(sessionId, options) {
      return callIdempotent('lock', sessionId, `/sessions/${sessionId}/bookings`, {
        method: 'POST',
        data: options,
      })
    },
    confirm(bookingId) {
      return callIdempotent('confirm', bookingId, `/bookings/${bookingId}/confirm`, {
        method: 'POST',
      })
    },
    cancel(bookingId) {
      return callIdempotent('cancel', bookingId, `/bookings/${bookingId}/cancel`, {
        method: 'POST',
      })
    },
    list() {
      return call('/member/bookings')
    },
  },

  waitlist: {
    join(sessionId) {
      return callIdempotent('waitlist', sessionId, `/sessions/${sessionId}/waitlist`, {
        method: 'POST',
      })
    },
    cancel(waitlistId) {
      return call(`/waitlist/${waitlistId}`, { method: 'DELETE' })
    },
  },

  checkin: {
    info() {
      return call('/member/checkin')
    },
    auto(venueCode, options) {
      const identity = `${venueCode}-${todayKey()}`
      return callIdempotent('scan', identity, '/checkins/scan', {
        method: 'POST',
        data: Object.assign({ venueCode }, options),
      })
    },
    free(venueCode, options) {
      const identity = `${venueCode}-${todayKey()}`
      return callIdempotent('free', identity, '/checkins/free', {
        method: 'POST',
        data: Object.assign({ venueCode }, options),
      })
    },
  },

  archive: {
    get(month) {
      return call('/member/archive', month ? { data: { month } } : {})
    },
    addBodyLog(payload) {
      return call('/member/body-logs', {
        method: 'POST',
        data: payload,
      })
    },
    updateProfile(payload) {
      return call('/member/me', {
        method: 'PATCH',
        data: payload,
      })
    },
  },

  mine: {
    get() {
      return call('/member/account')
    },
    updateProfile(payload) {
      return call('/member/me', {
        method: 'PATCH',
        data: payload,
      })
    },
  },

  messages: {
    list() {
      return call('/member/messages')
    },
    markRead(id) {
      return call(`/member/messages/${id}/read`, { method: 'POST' })
    },
    setSubscription(key, value) {
      return call('/member/subscriptions', {
        method: 'PATCH',
        data: { key, value },
      })
    },
  },

  invite: {
    get(ref, programCode) {
      return call(`/invites/${ref || 'guest'}`, {
        data: { program: programCode },
      })
    },
    me() {
      return call('/invites/me')
    },
    accept(ref) {
      return call(`/invites/${ref}/accept`, { method: 'POST' })
    },
  },

  community: {
    get() {
      return call('/community')
    },
    joinEvent(eventId) {
      return call(`/events/${eventId}/signups`, { method: 'POST' })
    },
    joinChallenge() {
      return call('/challenges/current/join', { method: 'POST' })
    },
  },
}
