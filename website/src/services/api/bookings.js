import { clearPendingKey, pendingKey } from './idempotency.js'
import { request } from './http.js'

export const createBooking = (input) => {
  const payload = { ...input, autoConfirm: true }
  const pending = pendingKey('booking', JSON.stringify(payload))
  return request('/bookings', {
    method: 'POST',
    body: payload,
    idempotencyKey: pending.key,
    // 体验课手机号已通过 OTP 验证，带上会员 token；后端允许 0 次卡会员预约 PLAN-01。
    auth: true,
  }).then((res) => {
    clearPendingKey('booking')
    const booking = res?.booking || res
    return res?.session ? { ...booking, session: res.session } : booking
  })
}
