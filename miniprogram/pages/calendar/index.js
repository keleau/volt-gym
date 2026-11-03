const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const dateUtil = require('../../utils/date')
const format = require('../../utils/format')

Page({
  data: {
    nav: {},
    loading: true,
    records: [],
    recordDates: [],
    calendarMonth: '',
    calendarMonthCount: 0,
    monthCountLabel: '',
    selectedDate: '',
    selectedDateLabel: '',
    dayRecords: [],
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  load(month) {
    const target = month || this.data.calendarMonth || dateUtil.currentMonth()
    this.loadToken = (this.loadToken || 0) + 1
    const token = this.loadToken
    this.setData({ loading: true })
    return api.archive
      .get(target)
      .then((data) => {
        if (token !== this.loadToken) return
        const records = data.records || []
        const current = data.month || target
        this.setData({
          loading: false,
          records,
          recordDates: data.recordDates || [],
          calendarMonth: current,
          calendarMonthCount: records.length,
          monthCountLabel: `${Number(current.split('-')[1])} 月`,
        })
      })
      .catch((error) => {
        if (token !== this.loadToken) return
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onCalendarChange(event) {
    const month = event.detail.month
    if (!month) return
    this.setData({
      selectedDate: '',
      selectedDateLabel: '',
      dayRecords: [],
    })
    this.load(month)
  },

  onDayTap(event) {
    const date = event.detail.date
    const month = date.slice(0, 7)
    const dayRecords = this.data.records
      .filter((record) => record.date === date)
      .map((record) => ({
        id: record.id,
        name: record.programName || '训练',
        time: record.startTime || record.time || '',
        coachName: record.coachName || record.coach || '',
        venueName: record.venueName || '',
        durationMin: record.durationMin || 0,
        kcal: record.kcal || 0,
      }))
    this.setData({
      selectedDate: date,
      selectedDateLabel: `${dateUtil.shortDate(date)} ${dateUtil.weekdayCn(date)}`,
      calendarMonth: month,
      calendarMonthCount: this.data.records.filter((record) => record.date.startsWith(month)).length,
      monthCountLabel: `${Number(month.split('-')[1])} 月`,
      dayRecords,
    })
  },

  onClearDay() {
    this.setData({ selectedDate: '', selectedDateLabel: '', dayRecords: [] })
  },
})
