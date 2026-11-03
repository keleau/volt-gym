const DAY = 86400000

export function todayIso() {
  return toIso(new Date())
}

export function addDaysIso(days) {
  return toIso(new Date(Date.now() + days * DAY))
}

export function toIso(date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}

export function formatCnDate(iso) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(iso || ''))
  if (!match) return ''
  return `${Number(match[2])} 月 ${match[3]} 日`
}

export function isPastDate(iso) {
  return !iso || iso < todayIso()
}

export function isBeyondDays(iso, days) {
  return !iso || iso > addDaysIso(days)
}
