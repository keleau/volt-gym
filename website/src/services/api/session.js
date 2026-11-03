import { WEB_SESSION_KEY } from './config.js'
import { readStore, writeStore } from './storage.js'

export function getWebSession() {
  if (typeof localStorage === 'undefined') return null
  let raw
  try {
    raw = localStorage.getItem(WEB_SESSION_KEY)
  } catch {
    return null
  }
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return typeof parsed === 'string' ? { token: parsed } : parsed
  } catch {
    return { token: raw }
  }
}

export function getWebToken() {
  return getWebSession()?.token || ''
}

export function saveWebSession(session) {
  writeStore(WEB_SESSION_KEY, session)
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('volt:session-changed', { detail: session }))
  }
}

export function clearWebSession() {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.removeItem(WEB_SESSION_KEY)
  } catch {
    /* ignore blocked storage */
  }
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('volt:session-expired'))
  }
}
