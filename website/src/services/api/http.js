import { API_BASE, REQUEST_TIMEOUT_MS } from './config.js'
import { ApiError } from './errors.js'
import { clearWebSession, getWebToken } from './session.js'

const FIELD_ALIASES = {
  venueCode: 'venue',
  programNo: 'program',
  planCode: 'plan',
}

function authHeaders() {
  const token = getWebToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function mapFields(fields) {
  if (!fields || typeof fields !== 'object') return null
  const mapped = { ...fields }
  for (const [backendKey, formKey] of Object.entries(FIELD_ALIASES)) {
    if (fields[backendKey]) {
      mapped[formKey] = fields[backendKey]
      delete mapped[backendKey]
    }
  }
  return mapped
}

export async function request(
  path,
  { method = 'GET', body, idempotencyKey, retryAnonymous = true, auth = true, headers = {} } = {}
) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(auth ? authHeaders() : {}),
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new ApiError('请求超时，请检查网络后再试。', { code: 'TIMEOUT' })
    }
    throw new ApiError('网络连接失败，请检查网络后再试。', { code: 'NETWORK_ERROR' })
  } finally {
    clearTimeout(timer)
  }

  const data = await response.json().catch(() => null)
  if (response.status === 401 && auth) {
    clearWebSession()
    // 读取类请求清掉失效会话后按游客重试一次；写请求不重试，避免重复下单
    if (method === 'GET' && retryAnonymous) {
      return request(path, { method, body, idempotencyKey, retryAnonymous: false, auth })
    }
  }

  if (!response.ok) {
    const error = new ApiError(data?.message || data?.msg || '请求失败，请稍后再试。', {
      status: response.status,
      code: data?.code || 'SERVER_ERROR',
      fields: mapFields(data?.fields),
    })
    if (data?.requestId) error.requestId = data.requestId
    throw error
  }

  if (response.status === 204) return null
  if (data === null) {
    throw new ApiError('服务返回格式异常，请稍后再试。', {
      status: response.status,
      code: 'SERVER_ERROR',
    })
  }
  return data
}
