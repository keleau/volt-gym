import { request } from './http.js'

export const sendOtp = (phone) =>
  request('/auth/otp/send', {
    method: 'POST',
    body: { phone },
    retryAnonymous: false,
  })

export const verifyOtp = (phone, code) =>
  request('/auth/otp/verify', {
    method: 'POST',
    body: { phone, code },
    retryAnonymous: false,
  })

export const getMemberHome = () => request('/member/home', { retryAnonymous: false })
