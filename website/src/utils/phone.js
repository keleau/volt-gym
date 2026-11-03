export function normalizePhone(value) {
  return String(value || '').replace(/\D/g, '').slice(0, 11)
}

export function isValidPhone(value) {
  return /^1[3-9]\d{9}$/.test(String(value || ''))
}
