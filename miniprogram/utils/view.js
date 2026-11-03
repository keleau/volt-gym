const catalog = require('../data/catalog')
const dateUtil = require('./date')

const PROGRAM_IMAGE = {}
const PROGRAM_NAME = {}
;(catalog.programs || []).forEach((program) => {
  PROGRAM_IMAGE[program.no] = program.image
  PROGRAM_NAME[program.no] = program.name
})

const PROGRAM_FALLBACK = '/assets/images/program-boxing.webp'
const FREE_IMAGE = '/assets/images/gym-space.webp'

function programImage(programNo) {
  return PROGRAM_IMAGE[programNo] || PROGRAM_FALLBACK
}

function programName(programNo) {
  return PROGRAM_NAME[programNo] || '训练'
}

function storyImage(img) {
  if (!img) return '/assets/images/coach-01.webp'
  if (img.indexOf('coach-03') >= 0) return '/assets/images/coach-03.webp'
  if (img.indexOf('coach-02') >= 0) return '/assets/images/coach-02.webp'
  return '/assets/images/coach-01.webp'
}

function eventMonthShort(month) {
  return String(month || '').split('·')[0].trim()
}

function recordImage(record) {
  const code = record.programNo || record.programCode
  return code === 'FREE' ? FREE_IMAGE : programImage(code)
}

function recordSummary(record) {
  return {
    id: record.id,
    name: record.programName || programName(record.programNo || record.programCode),
    time: record.startTime || record.time || '',
    coachName: record.coachName || record.coach || '',
    venueName: record.venueName || '',
    durationMin: record.durationMin || 0,
    kcal: record.kcal || 0,
  }
}

function weekdayLabel(date) {
  return dateUtil.weekdayCn(date)
}

module.exports = {
  programImage,
  programName,
  storyImage,
  eventMonthShort,
  recordImage,
  recordSummary,
  weekdayLabel,
}
