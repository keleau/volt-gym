import { clearPendingKey, pendingKey } from './idempotency.js'
import { request } from './http.js'

export const createBooking = (input) => {
  const payload = { ...input, autoConfirm: true }
  const pending = pendingKey('booking', JSON.stringify(payload))
  return request('/bookings', {
    method: 'POST',
    body: payload,
    idempotencyKey: pending.key,
    // 官网只卖体验课，按游客单处理；会员次卡约课在小程序。
    auth: false,
  }).then((res) => {
    clearPendingKey('booking')
    const booking = res?.booking || res
    return res?.session ? { ...booking, session: res.session } : booking
  })
}
