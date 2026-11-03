import { clearPendingKey, pendingKey } from './idempotency.js'
import { request } from './http.js'

function withVenueAlias(event) {
  if (!event || typeof event !== 'object') return event
  return { ...event, venue: event.venue || event.venueName || '' }
}

function normalizeEvent(event) {
  const cap = Number(event.cap) || 0
  const signed = Math.min(cap, Number(event.signed) || 0)
  const remaining = Number.isFinite(Number(event.remaining))
    ? Number(event.remaining)
    : Math.max(0, cap - signed)
  return withVenueAlias({
    ...event,
    cap,
    signed,
    remaining,
    soldOut: remaining <= 0 || Boolean(event.soldOut),
  })
}

function normalizeCommunity(data) {
  if (!data || typeof data !== 'object') return data
  return {
    ...data,
    events: Array.isArray(data.events) ? data.events.map(normalizeEvent) : [],
  }
}

export const listCommunity = () => request('/community').then(normalizeCommunity)

export const joinEvent = (eventId, input) => {
  const scope = `event:${eventId}`
  const pending = pendingKey(scope, JSON.stringify(input))
  return request(`/events/${encodeURIComponent(eventId)}/signups`, {
    method: 'POST',
    body: input,
    idempotencyKey: pending.key,
  }).then((res) => {
    clearPendingKey(scope)
    return res?.event ? { ...res, event: normalizeEvent(res.event) } : res
  })
}

export const joinChallenge = (input) => {
  const pending = pendingKey('challenge', JSON.stringify(input))
  return request('/challenges/current/join', {
    method: 'POST',
    body: input,
    idempotencyKey: pending.key,
  }).then((res) => {
    clearPendingKey('challenge')
    return res
  })
}
