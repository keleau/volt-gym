const api = require('../../services/api')
const auth = require('../../services/auth')
const navUtil = require('../../utils/nav')
const tab = require('../../utils/tab')
const share = require('../../utils/share')
const format = require('../../utils/format')

Page({
  data: {
    nav: {},
    loading: true,
    error: '',
    invite: null,
    program: null,
    ref: '',
    from: '',
    loginVisible: false,
    claiming: false,
    canBind: false,
    accepted: false,
    scrollTop: 0,
    programCode: 'P01',
  },

  onLoad(options) {
    let from = ''
    if (options && options.from) {
      try {
        from = decodeURIComponent(options.from)
      } catch (error) {
        from = options.from
      }
    }
    this.setData({
      nav: navUtil.getNavMetrics(),
      ref: (options && options.ref) || '',
      from,
      programCode: (options && options.program) || 'P01',
    })
    this.load((options && options.program) || 'P01')
  },

  load(programCode) {
    this.setData({ loading: true, error: '' })
    api.invite
      .get(this.data.ref, programCode)
      .then((invite) => {
        this.authRetried = false
        this.setData({
          loading: false,
          invite,
          program: invite.program,
          canBind: Boolean(this.data.ref) && !invite.isSelf,
        })
      })
      .catch((error) => {
        // 邀请页是公开落地页：旧 token 失效时清掉会话再试一次，不把用户挡在错误页。
        if (error && error.code === 'UNAUTHORIZED' && !this.authRetried) {
          this.authRetried = true
          this.load(programCode)
          return
        }
        this.setData({ loading: false, error: format.errorText(error) })
      })
  },

  onRetry() {
    this.load(this.data.programCode || 'P01')
  },

  onBack() {
    const pages = getCurrentPages()
    if (pages.length > 1) wx.navigateBack()
    else wx.switchTab({ url: '/pages/index/index' })
  },

  onClaim() {
    if (!auth.isLoggedIn()) {
      this.setData({ loginVisible: true })
      return
    }
    this.claimAndGo()
  },

  claimAndGo() {
    if (!this.data.canBind || this.data.accepted) {
      this.goSchedule()
      return
    }
    if (this.data.claiming) return
    this.setData({ claiming: true })
    api.invite
      .accept(this.data.ref)
      .then((result) => {
        this.setData({ claiming: false, accepted: true })
        wx.showToast({ title: result.already ? '邀请已绑定过' : '邀请已绑定', icon: 'none' })
        this.goSchedule()
      })
      .catch((error) => {
        this.setData({ claiming: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  goSchedule() {
    const program = this.data.program
    tab.switchTab('/pages/schedule/index', {
      schedulePreset: { programCode: program ? program.no : 'P01' },
    })
  },

  onLoginSuccess() {
    this.setData({ loginVisible: false })
    this.claimAndGo()
  },

  onLoginCancel() {
    this.setData({ loginVisible: false })
  },

  onCall() {
    wx.makePhoneCall({ phoneNumber: '4009006688' })
  },

  onShareAppMessage() {
    const member = auth.getUser()
    const program = this.data.program
    return share.inviteShare({
      program: program ? program.no : 'P01',
      ref: member ? member.referralCode : this.data.invite && this.data.invite.referralCode,
      from: member ? member.nickname : '',
      title: program
        ? `一起练 ${program.name}，VOLT 首次体验 ¥49，不办年卡`
        : 'VOLT 电压健身｜首次体验 ¥49，不办年卡',
      imageUrl: program ? program.image : '/assets/images/program-boxing.webp',
    })
  },

  onShareTimeline() {
    const program = this.data.program
    return {
      title: 'VOLT 电压健身｜首次体验 ¥49，不办年卡',
      query: `program=${program ? program.no : 'P01'}`,
      imageUrl: '/assets/images/hero.jpg',
    }
  },
})
