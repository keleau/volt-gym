const dateUtil = require('../../utils/date')

Component({
  properties: {
    month: { type: String, value: '' },
    markedDates: { type: Array, value: [] },
    selected: { type: String, value: '' },
    allowFuture: { type: Boolean, value: false },
  },

  data: {
    weeks: [],
    label: '',
    canNext: false,
    weekdayLabels: ['一', '二', '三', '四', '五', '六', '日'],
  },

  observers: {
    'month, markedDates, selected': function () {
      this.build()
    },
  },

  lifetimes: {
    attached() {
      if (!this.data.month) {
        this.setData({ month: dateUtil.currentMonth() }, () => this.build())
      } else {
        this.build()
      }
    },
  },

  methods: {
    build() {
      if (!this.data.month) return
      const marked = {}
      ;(this.data.markedDates || []).forEach((iso) => {
        marked[iso] = true
      })
      const weeks = dateUtil.monthMatrix(this.data.month).map((week, index) => ({
        weekId: `w${index}`,
        days: week.days.map((day) => Object.assign({}, day, {
          marked: !!marked[day.iso],
          selected: day.iso === this.data.selected,
        })),
      }))
      this.setData({
        weeks,
        label: dateUtil.monthLabel(this.data.month),
        canNext: this.data.allowFuture || this.data.month < dateUtil.currentMonth(),
      })
    },

    onPrev() {
      const month = dateUtil.shiftMonth(this.data.month, -1)
      this.setData({ month }, () => {
        this.build()
        this.triggerEvent('change', { month })
      })
    },

    onNext() {
      if (!this.data.canNext) return
      const month = dateUtil.shiftMonth(this.data.month, 1)
      this.setData({ month }, () => {
        this.build()
        this.triggerEvent('change', { month })
      })
    },

    onDay(event) {
      const iso = event.currentTarget.dataset.date
      if (iso) this.triggerEvent('daytap', { date: iso })
    },
  },
})
