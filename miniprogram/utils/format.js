function pad(value, length) {
  return String(value).padStart(length || 2, '0')
}

function bmiMeta(bmi) {
  if (bmi < 18.5) return { label: '偏轻', tone: 'line' }
  if (bmi < 24) return { label: '标准', tone: 'paper' }
  if (bmi < 28) return { label: '偏高', tone: 'acid' }
  return { label: '高强度', tone: 'acid' }
}

function daysLeft(iso) {
  if (!iso) return 0
  const [year, month, day] = String(iso).split('-').map(Number)
  if (!year || !month || !day) return 0
  const today = new Date()
  const target = new Date(year, month - 1, day)
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const days = Math.round((target - base) / 86400000)
  return Number.isFinite(days) ? Math.max(0, days) : 0
}

function initials(name) {
  const text = String(name || 'VOLT').trim()
  return text.slice(0, 1).toUpperCase()
}

function maskPhone(phone) {
  if (!phone) return ''
  return String(phone).replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function errorText(error) {
  if (!error) return '操作失败，请重试'
  return error.message || '操作失败，请重试'
}

function classMeta(item) {
  if (!item) return ''
  return `${item.intensity || 'MID'} · ${item.durationMin || 60} MIN · ${item.kcal || 0} KCAL`
}

module.exports = {
  pad,
  bmiMeta,
  daysLeft,
  initials,
  maskPhone,
  money,
  errorText,
  classMeta,
}
