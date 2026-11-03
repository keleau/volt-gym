const PREFIX = 'volt.idem.v1.'

export function makeIdempotencyKey() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `idem_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

function readPending(scope) {
  if (typeof sessionStorage === 'undefined') return null
  try {
    const raw = sessionStorage.getItem(PREFIX + scope)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function writePending(scope, value) {
  if (typeof sessionStorage === 'undefined') return
  try {
    sessionStorage.setItem(PREFIX + scope, JSON.stringify(value))
  } catch {
    /* private mode: the request still works, it just cannot be replayed after reload */
  }
}

/** 同一页面会话内，同一请求签名复用键；网络失败后重试不会重复下单。 */
export function pendingKey(scope, signature) {
  const stored = readPending(scope)
  if (stored?.signature === signature && stored.key) return stored
  const next = { signature, key: makeIdempotencyKey() }
  writePending(scope, next)
  return next
}

export function clearPendingKey(scope) {
  if (typeof sessionStorage === 'undefined') return
  try {
    sessionStorage.removeItem(PREFIX + scope)
  } catch {
    /* ignore */
  }
}
