const api = require('../../services/api')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const tab = require('../../utils/tab')
const format = require('../../utils/format')
const subscribe = require('../../services/subscribe')
const share = require('../../utils/share')
const geo = require('../../services/geo')
const volt = require('../../data/volt')

function periodOf(time) {
  const minutes = dateUtil.timeToMinutes(time)
  if (minutes < 12 * 60) return { key: 'morning', label: '早晨', range: '06:00–12:00' }
  if (minutes < 17 * 60) return { key: 'noon', label: '午间', range: '12:00–17:00' }
  return { key: 'evening', label: '晚间', range: '17:00–23:00' }
}

Page({
  data: {
    nav: {},
    loading: true,
    loadingSessions: false,
    locating: false,
    venues: [],
    coaches: [],
    days: [],
    dayViews: [],
    programs: [],
    member: { credits: 0 },
    selectedVenueCode: '',
    selectedVenue: null,
    mode: 'coach',
    selectedCoachId: '',
    selectedCoach: null,
    selectedDate: '',
    selectedDateLabel: '',
    sessions: [],
    timeGroups: [],
    programNo: '',
    programName: '',
    venueCoaches: [],
    sheetVisible: false,
    sheetState: 'book',
    sheetTitle: '',
    sheetSubtitle: '',
    selected: null,
    reminder: true,
    submitting: false,
    bookingResult: null,
    waitlistResult: null,
    lockText: '',
    scrollTop: 0,
  },

  onLoad(options) {
    this._alive = true
    this.pendingPreset = Object.assign({}, options || {})
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  onShow() {
    tab.select(this, 1)
    tab.setHidden(this, false)
    const app = getApp()
    if (app.globalData.schedulePreset) {
      const preset = app.globalData.schedulePreset
      app.globalData.schedulePreset = null
      if (this.loadedOnce) this.applyPreset(preset)
      else this.pendingPreset = Object.assign({}, this.pendingPreset, preset)
    }
  },

  onHide() {
    this.stopLockTicker()
    if (this.data.sheetVisible) this.setData({ sheetVisible: false })
    tab.setHidden(this, false)
  },

  onUnload() {
    this._alive = false
    this.stopLockTicker()
  },

  load() {
    this.setData({ loading: true })
    return api.schedule
      .get()
      .then((data) => {
        if (!this._alive) return
        this.loadedOnce = true
        this.allSessions = data.sessions || []
        this.allDays = data.days || []
        this.setData({
          loading: false,
          venues: data.venues || [],
          coaches: data.coaches || [],
          days: data.days || [],
          programs: data.programs || [],
          member: data.member || { credits: 0 },
        })
        const preset = Object.assign({}, this.pendingPreset || {})
        this.pendingPreset = null
        const lastVenue = wx.getStorageSync(volt.lastVenueKey)
        let venueCode = preset.venueCode || lastVenue || ''
        if (!this.data.venues.some((venue) => venue.code === venueCode)) venueCode = ''
        if (preset.sessionId) this.openPresetSession(preset.sessionId, preset)
        else if (venueCode) this.selectVenueByCode(venueCode, preset)
        else this.chooseVenueState()
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  applyPreset(preset) {
    if (!preset) return
    this.pendingPreset = Object.assign({}, preset)
    if (!this.loadedOnce) return
    if (preset.sessionId) {
      this.openPresetSession(preset.sessionId, preset)
      return
    }
    if (preset.venueCode) {
      this.selectVenueByCode(preset.venueCode, preset)
      return
    }
    if (this.data.selectedVenueCode) this.selectVenueByCode(this.data.selectedVenueCode, preset)
  },

  chooseVenueState() {
    this.setData({
      selectedVenueCode: '',
      selectedVenue: null,
      selectedCoachId: '',
      selectedCoach: null,
      programNo: '',
      programName: '',
      sessions: [],
      timeGroups: [],
      dayViews: [],
    })
  },

  openPresetSession(sessionId, preset) {
    const session = this.allSessions.find((item) => item.id === sessionId)
    if (!session) {
      wx.showToast({ title: '分享的场次已下架', icon: 'none' })
      const fallbackVenue = preset.venueCode || ''
      if (fallbackVenue) this.selectVenueByCode(fallbackVenue, preset)
      else this.chooseVenueState()
      return
    }
    const venueCode = session.venueCode
    const sessionPreset = Object.assign({}, preset, {
      venueCode,
      coachId: session.coachId,
      programNo: session.programNo,
      date: session.date,
      mode: 'coach',
    })
    this.selectVenueByCode(venueCode, sessionPreset)
    setTimeout(() => {
      if (this._alive) this.openSessionByData(session)
    }, 260)
  },

  selectVenue(event) {
    const code = event.currentTarget.dataset.code
    if (!code) return
    this.selectVenueByCode(code, {})
  },

  selectVenueByCode(code, preset) {
    const venue = this.data.venues.find((item) => item.code === code)
    if (!venue) return
    wx.setStorageSync(volt.lastVenueKey, code)
    const venueCoaches = this.buildCoachViews(code)
    const wantedCoach =
      preset && preset.coachId
        ? venueCoaches.find((coach) => coach.id === preset.coachId)
        : null
    const mode = preset && preset.mode ? preset.mode : 'coach'
    const coachId = mode === 'coach' && wantedCoach ? wantedCoach.id : ''
    const programNo = (preset && (preset.programNo || preset.programCode)) || ''
    const program = programNo
      ? this.data.programs.find((item) => item.no === programNo)
      : null
    const selectedDate = this.chooseDateFor(
      code,
      mode,
      coachId,
      preset && preset.date,
      program ? programNo : '',
    )
    this.setData(
      {
        selectedVenueCode: code,
        selectedVenue: venue,
        venueCoaches,
        coaches: this.data.coaches,
        mode,
        selectedCoachId: coachId,
        selectedCoach: coachId
          ? venueCoaches.find((coach) => coach.id === coachId) || null
          : null,
        selectedDate,
        programNo: program ? programNo : '',
        programName: program ? program.name : '',
      },
      () => this.refreshView(),
    )
  },

  clearProgramFilter() {
    this.setData({ programNo: '', programName: '' }, () => this.refreshView())
  },

  buildCoachViews(venueCode) {
    return this.data.coaches
      .filter((coach) => !coach.venueCodes || coach.venueCodes.includes(venueCode))
      .map((coach) => {
        const future = this.allSessions
          .filter(
            (session) =>
              session.venueCode === venueCode && session.coachId === coach.id && !session.isPast,
          )
          .sort((a, b) => `${a.date} ${a.startTime}`.localeCompare(`${b.date} ${b.startTime}`))
        const next = future.find((session) => session.left > 0)
        return Object.assign({}, coach, {
          slotsLeft: future.filter((session) => session.left > 0).length,
          nextLabel: next
            ? `${dateUtil.relativeDay(next.date)} ${next.startTime}`
            : '本周暂无',
          programLabel: (coach.programNos || [])
            .map((no) => {
              const program = this.data.programs.find((item) => item.no === no)
              return program ? program.name : no
            })
            .join(' / '),
        })
      })
  },

  chooseDateFor(venueCode, mode, coachId, preferredDate, programNo) {
    const list = this.allSessions.filter(
      (session) =>
        session.venueCode === venueCode &&
        !session.isPast &&
        (!programNo || session.programNo === programNo) &&
        (mode !== 'coach' || (coachId && session.coachId === coachId)),
    )
    if (preferredDate && list.some((session) => session.date === preferredDate)) {
      return preferredDate
    }
    if (this.data.selectedDate && list.some((session) => session.date === this.data.selectedDate)) {
      return this.data.selectedDate
    }
    const first = list.sort((a, b) =>
      `${a.date} ${a.startTime}`.localeCompare(`${b.date} ${b.startTime}`),
    )[0]
    return first ? first.date : dateUtil.todayISO()
  },

  switchMode(event) {
    const mode = event.currentTarget.dataset.mode
    if (!mode || mode === this.data.mode) return
    const coachId = mode === 'coach' ? this.data.selectedCoachId : ''
    const selectedDate = this.chooseDateFor(
      this.data.selectedVenueCode,
      mode,
      coachId,
      this.data.selectedDate,
      this.data.programNo,
    )
    this.setData(
      {
        mode,
        selectedCoachId: coachId,
        selectedCoach:
          mode === 'coach'
            ? this.data.venueCoaches.find((coach) => coach.id === coachId) || null
            : null,
        selectedDate,
      },
      () => this.refreshView(),
    )
  },

  selectCoach(event) {
    const coachId = event.currentTarget.dataset.id
    const coach = this.data.venueCoaches.find((item) => item.id === coachId)
    if (!coach) return
    const selectedDate = this.chooseDateFor(
      this.data.selectedVenueCode,
      'coach',
      coachId,
      this.data.selectedDate,
      this.data.programNo,
    )
    this.setData(
      { mode: 'coach', selectedCoachId: coachId, selectedCoach: coach, selectedDate },
      () => this.refreshView(),
    )
  },

  changeCoach() {
    this.setData({ selectedCoachId: '', selectedCoach: null, selectedDate: '' }, () =>
      this.refreshView(),
    )
  },

  changeVenue() {
    this.chooseVenueState()
  },

  onLocate() {
    if (this.data.locating) return
    this.setData({ locating: true })
    geo
      .locate()
      .then((location) => {
        if (!location.ok) {
          wx.showToast({ title: '定位未授权，可直接选择门店', icon: 'none' })
          return
        }
        const venues = this.data.venues.map((venue) => {
          const geoInfo = volt.venueGeo[venue.code]
          if (!geoInfo) return venue
          const meters = geo.distanceMeters(location, geoInfo)
          return Object.assign({}, venue, {
            distanceMeters: meters,
            distanceText: meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(1)} km`,
          })
        })
        venues.sort((a, b) => {
          if (typeof a.distanceMeters !== 'number') return 1
          if (typeof b.distanceMeters !== 'number') return -1
          return a.distanceMeters - b.distanceMeters
        })
        this.setData({ venues })
        if (!this.data.selectedVenueCode && venues[0]) {
          this.selectVenueByCode(venues[0].code, {})
        }
      })
      .finally(() => this.setData({ locating: false }))
  },

  selectDate(event) {
    const date = event.currentTarget.dataset.date
    if (!date || date === this.data.selectedDate) return
    this.setData({ selectedDate: date }, () => this.refreshView())
  },

  refreshView() {
    const venueCode = this.data.selectedVenueCode
    if (!venueCode) return
    const mode = this.data.mode
    const coachId = this.data.selectedCoachId
    const selectedDate = this.data.selectedDate
    const currentDay = this.data.days.find((day) => day.iso === selectedDate)
    let list = this.allSessions.filter(
      (session) => session.venueCode === venueCode && !session.isPast && session.date === selectedDate,
    )
    if (mode === 'coach' && coachId) {
      list = list.filter((session) => session.coachId === coachId)
    }
    if (this.data.programNo) {
      list = list.filter((session) => session.programNo === this.data.programNo)
    }
    const sessions = list
      .map((session) => this.decorateSession(session))
      .sort((a, b) => a.startTime.localeCompare(b.startTime))
    const groups = [
      { key: 'morning', label: '早晨', range: '06:00–12:00', sessions: [] },
      { key: 'noon', label: '午间', range: '12:00–17:00', sessions: [] },
      { key: 'evening', label: '晚间', range: '17:00–23:00', sessions: [] },
    ]
    sessions.forEach((session) => {
      const period = periodOf(session.startTime)
      const group = groups.find((item) => item.key === period.key)
      if (group) group.sessions.push(session)
    })
    const dayViews = this.data.days.map((day) => {
      const daySessions = this.allSessions.filter(
        (session) =>
          session.date === day.iso &&
          session.venueCode === venueCode &&
          !session.isPast &&
          (!this.data.programNo || session.programNo === this.data.programNo) &&
          (mode !== 'coach' || !coachId || session.coachId === coachId),
      )
      return Object.assign({}, day, {
        availableCount: daySessions.filter((session) => session.left > 0).length,
        hasSessions: daySessions.length > 0,
        isActive: day.iso === selectedDate,
      })
    })
    this.setData({
      sessions,
      timeGroups: groups.filter((group) => group.sessions.length),
      dayViews,
      selectedDateLabel: currentDay
        ? `${dateUtil.relativeDay(selectedDate)} · ${currentDay.cn}`
        : dateUtil.relativeDay(selectedDate),
      loadingSessions: false,
    })
  },

  decorateSession(session) {
    let statusLabel = '可约'
    let actionLabel = '预约'
    let tone = 'go'
    if (session.myStatus === 'confirmed') {
      statusLabel = '已预约'
      actionLabel = '管理'
      tone = 'mine'
    } else if (session.myStatus === 'locked') {
      statusLabel = '锁位中'
      actionLabel = '确认'
      tone = 'mine'
    } else if (session.myStatus === 'waitlisted') {
      statusLabel = `候补 ${session.waitlistPosition}`
      actionLabel = '管理'
      tone = 'mine'
    } else if (session.left <= 0) {
      statusLabel = '已满'
      actionLabel = '候补'
      tone = 'full'
    }
    return Object.assign({}, session, {
      time: session.startTime || session.time,
      statusLabel,
      actionLabel,
      tone,
      tagClass: tone === 'mine' ? 'tag--acid' : tone === 'full' ? 'tag--danger' : 'tag--paper',
      period: periodOf(session.startTime || session.time),
    })
  },

  openSession(event) {
    const id = event.currentTarget.dataset.id
    const session = this.data.sessions.find((item) => item.id === id)
    if (session) this.openSessionByData(session)
  },

  openSessionByData(session) {
    if (!session) return
    if (session.isPast) {
      wx.showToast({ title: '这节课已经开始', icon: 'none' })
      return
    }
    let sheetState = 'book'
    if (session.myStatus) sheetState = 'manage'
    else if (session.left <= 0) sheetState = 'waitlist'
    const title = sheetState === 'manage'
      ? session.myStatus === 'waitlisted'
        ? '候补中'
        : session.myStatus === 'locked'
          ? '确认预约'
          : '已预约'
      : sheetState === 'waitlist'
        ? '加入候补'
        : '锁定名额'
    this.setData({
      selected: session,
      sheetVisible: true,
      sheetState,
      sheetTitle: title,
      sheetSubtitle: `${dateUtil.relativeDay(session.date)} ${session.startTime} · ${session.venueName}`,
      reminder: true,
      bookingResult: null,
      waitlistResult: null,
      lockText: '',
    })
    tab.setHidden(this, true)
    if (session.myStatus === 'locked' && session.myLockExpiresAt) {
      this.startLockTicker(session.myLockExpiresAt)
    } else {
      this.stopLockTicker()
    }
  },

  onCloseSheet() {
    const state = this.data.sheetState
    const selected = this.data.selected
    const stillLocked = state === 'locked'
      || (state === 'manage' && selected && selected.myStatus === 'locked')
    this.setData({ sheetVisible: false })
    tab.setHidden(this, false)
    // 锁位倒计时继续跑：关闭弹层后如果超时，课表会主动刷新释放名额。
    if (!stillLocked) this.stopLockTicker()
    if (stillLocked) {
      wx.showToast({ title: '名额已锁定，10 分钟内可确认', icon: 'none' })
    }
    if (state === 'confirmed' || state === 'waitlisted') this.load()
  },

  onToggleReminder(event) {
    this.setData({ reminder: event.detail.value })
  },

  startLockTicker(expiresAt) {
    this.stopLockTicker()
    const tick = () => {
      if (!this._alive) {
        this.stopLockTicker()
        return
      }
      const remain = expiresAt - Date.now()
      if (remain <= 0) {
        this.stopLockTicker()
        this.setData({ sheetVisible: false })
        tab.setHidden(this, false)
        wx.showToast({ title: '锁位超时，名额已释放', icon: 'none' })
        this.load()
        return
      }
      this.setData({ lockText: dateUtil.formatLock(remain) })
    }
    tick()
    this.lockTimer = setInterval(tick, 1000)
  },

  stopLockTicker() {
    if (this.lockTimer) {
      clearInterval(this.lockTimer)
      this.lockTimer = null
    }
  },

  updateSessionStatus(sessionId, patch) {
    this.allSessions = (this.allSessions || []).map((session) =>
      session.id === sessionId ? Object.assign({}, session, patch) : session,
    )
    const current = this.data.selected && this.data.selected.id === sessionId
      ? Object.assign({}, this.data.selected, patch)
      : this.data.selected
    this.setData({ selected: current })
    this.refreshView()
  },

  onBook() {
    const session = this.data.selected
    if (!session || this.data.submitting) return
    if (this.data.member.credits <= 0) {
      wx.showModal({
        title: '充电次数不足',
        content: '当前次卡余额为 0，请先查看次卡或联系门店。',
        showCancel: false,
        confirmText: '知道了',
        confirmColor: '#A33B2B',
      })
      return
    }
    if (session.left <= 0) {
      this.setData({ sheetState: 'waitlist', sheetTitle: '加入候补' })
      return
    }
    this.setData({ submitting: true })
    const keys = this.data.reminder ? ['classReminder', 'classChange'] : ['classChange']
    subscribe
      .requestSubscribe(keys)
      .then(() => api.bookings.create(session.id, { reminderOptIn: this.data.reminder }))
      .then((result) => {
        const booking = result.booking || result
        const status = booking.status
        const left = result.session && typeof result.session.left === 'number'
          ? result.session.left
          : Math.max(0, session.left - (result.duplicate ? 0 : 1))
        this.updateSessionStatus(session.id, {
          myStatus: status,
          myBookingId: booking.id,
          myLockExpiresAt: booking.lockExpiresAt || 0,
          left,
          soldOut: left <= 0,
        })
        this.setData({
          submitting: false,
          bookingResult: booking,
          sheetState: status === 'confirmed' ? 'confirmed' : 'locked',
          sheetTitle: status === 'confirmed' ? '已预约' : '确认预约',
        })
        if (status === 'locked' && booking.lockExpiresAt) {
          this.startLockTicker(booking.lockExpiresAt)
        }
        wx.showToast({
          title: result.duplicate ? '已预约过这一场' : '名额已锁定',
          icon: 'none',
        })
      })
      .catch((error) => {
        this.setData({ submitting: false })
        if (error && (error.code === 'SLOT_FULL' || error.code === 'SESSION_FULL')) {
          this.setData({ sheetState: 'waitlist', sheetTitle: '加入候补' })
        } else if (error && error.code === 'DUPLICATE_BOOKING') {
          wx.showToast({ title: '这一场已经约过', icon: 'none' })
          this.setData({ sheetVisible: false })
          tab.setHidden(this, false)
          this.load()
        } else if (error && (error.code === 'NO_CREDITS' || error.code === 'PASS_EXPIRED')) {
          this.setData({ sheetVisible: false })
          tab.setHidden(this, false)
          wx.showModal({
            title: error.code === 'PASS_EXPIRED' ? '次卡已过期' : '充电次数不足',
            content: error.message || '请查看次卡或联系门店。',
            showCancel: false,
            confirmText: '知道了',
            confirmColor: '#A33B2B',
          })
        } else {
          wx.showToast({ title: format.errorText(error), icon: 'none' })
        }
      })
  },

  confirmBooking() {
    const bookingId =
      (this.data.bookingResult && this.data.bookingResult.id) ||
      (this.data.selected && this.data.selected.myBookingId)
    if (!bookingId || this.data.submitting) return
    this.setData({ submitting: true })
    api.bookings
      .confirm(bookingId)
      .then((booking) => {
        this.stopLockTicker()
        this.updateSessionStatus(booking.sessionId, {
          myStatus: 'confirmed',
          myLockExpiresAt: 0,
        })
        this.setData({
          submitting: false,
          bookingResult: booking,
          sheetState: 'confirmed',
          sheetTitle: '预约成功',
        })
        wx.showToast({ title: '预约已确认', icon: 'success' })
      })
      .catch((error) => {
        this.setData({ submitting: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  releaseLock() {
    const bookingId =
      (this.data.bookingResult && this.data.bookingResult.id) ||
      (this.data.selected && this.data.selected.myBookingId)
    if (!bookingId) return
    wx.showModal({
      title: '释放已锁定名额？',
      content: '释放后名额回到课表，未扣充电次数。',
      confirmText: '释放名额',
      cancelText: '保留',
      confirmColor: '#A33B2B',
      success: (res) => {
        if (!res.confirm) return
        api.bookings
          .cancel(bookingId)
          .then((result) => {
            this.stopLockTicker()
            const left = result && result.session && typeof result.session.left === 'number'
              ? result.session.left
              : this.data.selected.left + 1
            this.updateSessionStatus(this.data.selected.id, {
              myStatus: '',
              myBookingId: '',
              myLockExpiresAt: 0,
              left,
              soldOut: left <= 0,
            })
            this.setData({ sheetVisible: false, bookingResult: null })
            tab.setHidden(this, false)
            wx.showToast({ title: '名额已释放', icon: 'none' })
          })
          .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
      },
    })
  },

  joinWaitlist() {
    const session = this.data.selected
    if (!session || this.data.submitting) return
    this.setData({ submitting: true })
    subscribe
      .requestSubscribe(['waitlist', 'classChange'])
      .then(() => api.waitlist.join(session.id))
      .then((entry) => {
        this.updateSessionStatus(session.id, {
          myStatus: 'waitlisted',
          myWaitlistId: entry.id,
          waitlistPosition: entry.position,
        })
        this.setData({
          submitting: false,
          waitlistResult: entry,
          sheetState: 'waitlisted',
          sheetTitle: '已进入候补',
        })
        wx.showToast({ title: `候补第 ${entry.position} 位`, icon: 'none' })
      })
      .catch((error) => {
        this.setData({ submitting: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  cancelExisting() {
    const session = this.data.selected
    if (!session) return
    const isWaitlist = session.myStatus === 'waitlisted'
    wx.showModal({
      title: isWaitlist ? '退出候补？' : '取消这节预约？',
      content: isWaitlist
        ? '退出后不再为你保留候补顺位。'
        : '开课前 2 小时可取消，不扣次数；锁位取消不扣次数。',
      confirmText: isWaitlist ? '退出候补' : '取消预约',
      cancelText: '保留',
      confirmColor: '#A33B2B',
      success: (res) => {
        if (!res.confirm) return
        const request = isWaitlist
          ? api.waitlist.cancel(session.myWaitlistId)
          : api.bookings.cancel(session.myBookingId)
        request
          .then((result) => {
            this.stopLockTicker()
            const left = result && result.session && typeof result.session.left === 'number'
              ? result.session.left
              : isWaitlist ? session.left : session.left + 1
            this.updateSessionStatus(session.id, {
              myStatus: '',
              myBookingId: '',
              myWaitlistId: '',
              myLockExpiresAt: 0,
              left,
              soldOut: left <= 0,
            })
            this.setData({ sheetVisible: false, bookingResult: null, waitlistResult: null })
            tab.setHidden(this, false)
            wx.showToast({ title: isWaitlist ? '已退出候补' : '预约已取消', icon: 'none' })
          })
          .catch((error) => {
            wx.showModal({
              title: '暂时无法取消',
              content: format.errorText(error),
              showCancel: false,
              confirmText: '知道了',
              confirmColor: '#A33B2B',
            })
          })
      },
    })
  },

  goBookings() {
    this.setData({ sheetVisible: false })
    tab.setHidden(this, false)
    wx.navigateTo({ url: '/pages/bookings/index' })
  },

  goCheckin() {
    this.setData({ sheetVisible: false })
    tab.setHidden(this, false)
    tab.switchTab('/pages/checkin/index')
  },

  onShareAppMessage() {
    const session = this.data.selected
    const member = this.data.member
    if (session) {
      return {
        title: `一起练 ${session.programName} · ${session.coachName} · ${dateUtil.relativeDay(session.date)} ${session.startTime}`,
        path: `/pages/schedule/index?sessionId=${session.id}&venueCode=${session.venueCode}&coachId=${session.coachId}`,
        imageUrl: session.programImage,
      }
    }
    return share.inviteShare({
      program: 'P01',
      ref: member.referralCode,
      title: 'VOLT 本周课表｜按次约课，不办年卡',
    })
  },

  onShareTimeline() {
    const session = this.data.selected
    return {
      title: session
        ? `VOLT ${session.programName} · ${session.startTime} ${session.venueName}`
        : 'VOLT 课程预约｜按次付费，三店通用',
      query: session ? `sessionId=${session.id}&venueCode=${session.venueCode}` : 'program=P01',
      imageUrl: session ? session.programImage : '/assets/images/program-boxing.webp',
    }
  },
})
