export const HOME_STEPS = [
  { id: 'top', no: '01', label: '接入', en: 'BOOT', tone: 'ink' },
  { id: 'stations', no: '02', label: '充电站', en: 'STATIONS', tone: 'ink' },
  { id: 'programs', no: '03', label: '课程', en: 'PROGRAMS', tone: 'ink' },
  { id: 'offer', no: '04', label: '预约', en: 'PRICE / BOOK', tone: 'ink' },
  { id: 'footer', no: '05', label: '页脚', en: 'FOOTER', tone: 'ink' },
]

export const NAV_MAIN = [
  { id: 'top', no: '01', label: '首页', en: 'HOME', i: 0 },
  { id: 'stations', no: '02', label: '充电站', en: 'STATIONS', i: 1 },
  { id: 'programs', no: '03', label: '课程', en: 'COURSES', i: 2 },
  { id: 'offer', no: '04', label: '预约', en: 'BOOK', i: 3 },
  { id: 'community', no: '—', label: '社区', en: 'COMMUNITY', page: true },
]

export const ROUTE_IDS = HOME_STEPS.map((step) => step.id)
