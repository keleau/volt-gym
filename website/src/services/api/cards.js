import { request } from './http.js'

export const createCardOrder = (input) =>
  request('/card-orders', {
    method: 'POST',
    body: input,
    // 购卡登记按手机号归属，不依赖登录态；已登录也不会把体验课/购卡记成次卡消耗。
    auth: false,
  })

export const getMemberCardOrders = () =>
  request('/member/card-orders', { retryAnonymous: false })

export const getStaffCardOrders = ({ key, phone = '', status = 'pending' }) => {
  const params = new URLSearchParams()
  if (phone) params.set('phone', phone)
  if (status) params.set('status', status)
  const query = params.toString()
  return request(`/staff/card-orders${query ? `?${query}` : ''}`, {
    auth: false,
    retryAnonymous: false,
    headers: { 'X-Staff-Key': key },
  })
}

export const activateStaffCardOrder = ({ key, orderNo, staffName = '' }) =>
  request(`/staff/card-orders/${encodeURIComponent(orderNo)}/activate`, {
    method: 'POST',
    auth: false,
    retryAnonymous: false,
    headers: {
      'X-Staff-Key': key,
      ...(staffName ? { 'X-Staff-Name': staffName } : {}),
    },
  })
