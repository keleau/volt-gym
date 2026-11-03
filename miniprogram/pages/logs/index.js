const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const dateUtil = require('../../utils/date')
const format = require('../../utils/format')
const view = require('../../utils/view')

function mapRecord(record) {
  const parts = String(record.date).split('-')
  const summary = view.recordSummary(record)
  return Object.assign(summary, {
    weekday: dateUtil.weekdayCn(record.date),
    day: parts[2],
    monthKey: `${parts[0]}-${parts[1]}`,
    modeLabel: record.mode === 'free' ? 'FREE' : 'CLASS',
  })
}

function groupRecords(records) {
  const groups = []
  records.forEach((record) => {
    let group = groups[groups.length - 1]
    if (!group || group.monthKey !== record.monthKey) {
      group = {
        monthKey: record.monthKey,
        label: record.monthKey.replace('-', ' / '),
        count: 0,
        records: [],
      }
      groups.push(group)
    }
    group.records.push(record)
    group.count += 1
  })
  return groups
}

Page({
  data: {
    nav: {},
    loading: true,
    tab: 'records',
    totalSessions: 0,
    totalHours: 0,
    totalKcal: '0',
    groups: [],
    ranking: [],
    myRank: null,
    weekSessions: 0,
  },

  onLoad(options) {
    this.setData({
      nav: navUtil.getNavMetrics(),
      tab: options && options.tab === 'ranking' ? 'ranking' : 'records',
    })
    this.load()
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true })
    return Promise.all([api.archive.get(), api.community.get()])
      .then(([archive, community]) => {
        const stats = archive.stats || {}
        const records = (archive.records || []).map(mapRecord)
        const ranking = community.ranking || []
        this.setData({
          loading: false,
          totalSessions: records.length,
          totalHours: stats.totalHours || 0,
          totalKcal: format.money(stats.totalKcal || 0),
          groups: groupRecords(records),
          ranking,
          myRank: ranking.find((item) => item.me) || null,
          weekSessions: stats.weekSessions || 0,
        })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onSwitchTab(event) {
    const tab = event.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    this.setData({ tab })
  },
})
