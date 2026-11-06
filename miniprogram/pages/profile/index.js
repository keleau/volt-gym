const api = require('../../services/api')
const auth = require('../../services/auth')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')
const tab = require('../../utils/tab')

Page({
  data: {
    nav: {},
    loading: true,
    saving: false,
    uploadingAvatar: false,
    avatarPreview: '',
    member: {},
    memberInitial: '电',
    stats: {},
    form: {
      nickname: '',
      goal: '',
      heightCm: '',
      weightKg: '',
    },
    error: '',
  },

  onLoad() {
    this._alive = true
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  onUnload() {
    this._alive = false
  },

  load() {
    return api.mine
      .get()
      .then((data) => {
        if (!this._alive) return
        const member = data.member || {}
        this.setData({
          loading: false,
          member,
          memberInitial: member.avatarText || format.initials(member.nickname),
          stats: data.stats || {},
          form: {
            nickname: member.nickname || '',
            goal: member.goal || '',
            heightCm: member.heightCm ? String(member.heightCm) : '',
            weightKg: member.weightKg ? String(member.weightKg) : '',
          },
          error: '',
        })
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ loading: false, error: format.errorText(error) })
      })
  },

  chooseAvatar() {
    if (this.data.uploadingAvatar) return
    const pick = wx.chooseMedia
      ? new Promise((resolve, reject) => {
          wx.chooseMedia({
            count: 1,
            mediaType: ['image'],
            sourceType: ['album', 'camera'],
            sizeType: ['compressed'],
            success: (res) => resolve(res.tempFiles && res.tempFiles[0]),
            fail: reject,
          })
        })
      : new Promise((resolve, reject) => {
          wx.chooseImage({
            count: 1,
            sizeType: ['compressed'],
            sourceType: ['album', 'camera'],
            success: (res) => resolve({
              tempFilePath: res.tempFilePaths && res.tempFilePaths[0],
              size: res.tempFiles && res.tempFiles[0] && res.tempFiles[0].size,
            }),
            fail: reject,
          })
        })
    pick
      .then((file) => {
        if (!file || !file.tempFilePath) return
        if (file.size && file.size > 5 * 1024 * 1024) {
          this.setData({ error: '头像不能超过 5MB。' })
          return
        }
        this.setData({
          uploadingAvatar: true,
          avatarPreview: file.tempFilePath,
          error: '',
        })
        return api.mine.uploadAvatar(file.tempFilePath).then((member) => {
          if (!this._alive) return
          auth.refreshUser(member)
          this.setData({
            uploadingAvatar: false,
            avatarPreview: '',
            member,
            memberInitial: member.avatarText || format.initials(member.nickname),
          })
          wx.showToast({ title: '头像已更新', icon: 'success' })
        })
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ uploadingAvatar: false, avatarPreview: '' })
        const message = String((error && error.errMsg) || '')
        if (message.indexOf('cancel') >= 0) return
        this.setData({ error: format.errorText(error) })
      })
  },

  onInput(event) {
    const key = event.currentTarget.dataset.key
    this.setData({ [`form.${key}`]: event.detail.value, error: '' })
  },

  save() {
    if (this.data.saving) return
    const nickname = String(this.data.form.nickname || '').trim()
    const goal = String(this.data.form.goal || '').trim()
    if (!nickname) {
      this.setData({ error: '请填写昵称。' })
      return
    }
    const payload = { nickname, goal }
    const height = Number(this.data.form.heightCm)
    const weight = Number(this.data.form.weightKg)
    if (this.data.form.heightCm) {
      if (!Number.isFinite(height) || height < 120 || height > 230) {
        this.setData({ error: '身高需在 120–230 cm 之间。' })
        return
      }
      payload.heightCm = Math.round(height)
    }
    if (this.data.form.weightKg) {
      if (!Number.isFinite(weight) || weight < 30 || weight > 250) {
        this.setData({ error: '体重需在 30–250 kg 之间。' })
        return
      }
      payload.weightKg = weight
    }
    this.setData({ saving: true, error: '' })
    api.mine
      .updateProfile(payload)
      .then((member) => {
        if (!this._alive) return
        auth.refreshUser(member)
        this.setData({
          saving: false,
          member,
          memberInitial: member.avatarText || format.initials(member.nickname),
        })
        wx.showToast({ title: '资料已保存', icon: 'success' })
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ saving: false, error: format.errorText(error) })
      })
  },

  onGoArchive() {
    tab.switchTab('/pages/archive/index')
  },
})
