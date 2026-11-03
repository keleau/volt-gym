const WEEK_CN = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const WEEK_SHORT = ['日', '一', '二', '三', '四', '五', '六']
const DAY_KEYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

function pad(value) {
  return String(value).padStart(2, '0')
}

function parseISO(iso) {
  const [year, month, day] = String(iso).split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatISO(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function todayISO() {
  return formatISO(new Date())
}

function addDays(date, amount) {
  const next = new Date(date.getTime())
  next.setDate(next.getDate() + amount)
  return next
}

function isoAddDays(iso, amount) {
  return formatISO(addDays(parseISO(iso), amount))
}

function weekdayCn(iso) {
  return WEEK_CN[parseISO(iso).getDay()]
}

function weekdayShort(iso) {
  return WEEK_SHORT[parseISO(iso).getDay()]
}

function weekdayKey(iso) {
  return DAY_KEYS[parseISO(iso).getDay()]
}

function shortDate(iso) {
  const date = parseISO(iso)
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

function longDate(iso) {
  const date = parseISO(iso)
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日`
}

function relativeDay(iso) {
  const today = todayISO()
  if (iso === today) return '今天'
  if (iso === isoAddDays(today, 1)) return '明天'
  if (iso === isoAddDays(today, 2)) return '后天'
  if (iso === isoAddDays(today, -1)) return '昨天'
  return `${longDate(iso)} ${weekdayCn(iso)}`
}

function combineDateTime(iso, time) {
  const date = parseISO(iso)
  const [hour, minute] = String(time).split(':').map(Number)
  date.setHours(hour, minute, 0, 0)
  return date
}

function formatClock(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function timeToMinutes(time) {
  const [hour, minute] = String(time).split(':').map(Number)
  return hour * 60 + minute
}

function addMinutesToTime(time, minutes) {
  const total = (timeToMinutes(time) + Number(minutes || 0)) % (24 * 60)
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`
}

function durationFromMeta(meta) {
  const match = String((meta && meta.duration) || '60 MIN').match(/(\d+)/)
  return match ? Number(match[1]) : 60
}

function minutesUntil(iso, time, now) {
  const target = combineDateTime(iso, time)
  return Math.round((target.getTime() - (now || new Date()).getTime()) / 60000)
}

function formatLock(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`
}

function startOfWeek(date) {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const day = next.getDay()
  const diff = day === 0 ? -6 : 1 - day
  next.setDate(next.getDate() + diff)
  return next
}

function weekKeyOf(dateOrIso) {
  const date = typeof dateOrIso === 'string' ? parseISO(dateOrIso) : dateOrIso
  return formatISO(startOfWeek(date))
}

function sameDay(a, b) {
  return a === b
}

function monthOf(iso) {
  return String(iso).slice(0, 7)
}

function currentMonth() {
  return todayISO().slice(0, 7)
}

function monthLabel(month) {
  const [year, mon] = String(month).split('-')
  return `${year} / ${mon}`
}

function shiftMonth(month, delta) {
  const [year, mon] = String(month).split('-').map(Number)
  const date = new Date(year, mon - 1 + delta, 1)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}

function monthMatrix(month) {
  const [year, mon] = String(month).split('-').map(Number)
  const first = new Date(year, mon - 1, 1)
  const offset = first.getDay() === 0 ? 6 : first.getDay() - 1
  const start = addDays(first, -offset)
  const today = todayISO()
  const weeks = []
  for (let w = 0; w < 6; w += 1) {
    const days = []
    for (let d = 0; d < 7; d += 1) {
      const date = addDays(start, w * 7 + d)
      const iso = formatISO(date)
      days.push({
        iso,
        day: date.getDate(),
        inMonth: date.getMonth() === mon - 1,
        isToday: iso === today,
      })
    }
    weeks.push({ days })
  }
  return weeks
}

function greeting(now) {
  const hour = (now || new Date()).getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function stamp(ts) {
  const date = new Date(ts)
  const iso = formatISO(date)
  const today = todayISO()
  if (iso === today) return `今天 ${formatClock(date)}`
  if (iso === isoAddDays(today, -1)) return `昨天 ${formatClock(date)}`
  return `${shortDate(iso)} ${formatClock(date)}`
}

function weekLabels(count, now) {
  const currentStart = startOfWeek(now || new Date())
  const weeks = []
  for (let i = count - 1; i >= 0; i -= 1) {
    const start = addDays(currentStart, -7 * i)
    weeks.push({
      key: formatISO(start),
      label: shortDate(formatISO(start)),
      endISO: isoAddDays(formatISO(start), 6),
    })
  }
  return weeks
}

function computeWeekStreak(records, now) {
  if (!records || !records.length) return 0
  const weeks = new Set(records.map((item) => weekKeyOf(item.date)))
  const current = startOfWeek(now || new Date())
  let cursor = new Date(current.getTime())
  if (!weeks.has(formatISO(cursor))) {
    cursor = addDays(cursor, -7)
    if (!weeks.has(formatISO(cursor))) return 0
  }
  let streak = 0
  while (weeks.has(formatISO(cursor))) {
    streak += 1
    cursor = addDays(cursor, -7)
  }
  return streak
}

function daysAgoISO(amount) {
  return isoAddDays(todayISO(), -amount)
}

module.exports = {
  WEEK_CN,
  WEEK_SHORT,
  DAY_KEYS,
  pad,
  parseISO,
  formatISO,
  todayISO,
  addDays,
  isoAddDays,
  weekdayCn,
  weekdayShort,
  weekdayKey,
  shortDate,
  longDate,
  relativeDay,
  combineDateTime,
  formatClock,
  timeToMinutes,
  addMinutesToTime,
  durationFromMeta,
  minutesUntil,
  formatLock,
  startOfWeek,
  weekKeyOf,
  monthOf,
  currentMonth,
  monthLabel,
  shiftMonth,
  monthMatrix,
  greeting,
  stamp,
  weekLabels,
  computeWeekStreak,
  daysAgoISO,
}
