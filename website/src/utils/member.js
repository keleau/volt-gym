import { todayIso } from './date.js'

export function isMemberBlocked(member) {
  if (!member) return false
  const expired = member.expiresAt && member.expiresAt < todayIso()
  return Number(member.credits) <= 0 || Boolean(expired)
}
