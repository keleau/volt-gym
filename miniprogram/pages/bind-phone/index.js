const api = require('../../services/api')
const auth = require('../../services/auth')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')

Page({
  data: {
    nav: {},
    phone: '',
    code: '',
    codeSent: false,
    sending: false,
    verifying: false,
    cooldown: 0,
    devCode: '',
    error: '',
  },

  onLoad() {
    this._alive = true
    this.setData({ nav: navUtil.getNavMetrics() })
    auth.bootstrap()
      .then((session) => {
        if (!this._alive) return
        if (!session) {
          this.setData({ error: '登录状态初始化失败，请重新进入小程序。' })
          return
        }
        const member = session.user
        if (member && member.phoneBound) {
          this.leave()
          return
        }
        this.checkMember()
      })
      .catch((error) => {
        if (this._alive) this.setData({ error: format.errorText(error) })
      })
  },

  onUnload() {
    this._alive = false
    this.stopCooldown()
  },

  checkMember() {
    api.mine
      .get()
      .then((data) => {
        if (!this._alive) return
        if (data.member && data.member.phoneBound) {
          auth.applyMember(data.member)
          this.leave()
        }
      })
      .catch(() => {})
  },

  onPhoneInput(event) {
    const phone = String(event.detail.value || '').replace(/\D/g, '').slice(0, 11)
    this.setData({ phone, error: '' })
  },

  onCodeInput(event) {
    const code = String(event.detail.value || '').replace(/\D/g, '').slice(0, 6)
    this.setData({ code, error: '' })
  },

  sendCode() {
    if (this.data.sending || this.data.cooldown > 0) return
    const phone = String(this.data.phone || '').trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      this.setData({ error: '请输入正确的 11 位手机号。' })
      return
    }
    this.setData({ sending: true, error: '' })
    api.auth
      .sendBindCode(phone)
      .then((res) => {
        if (!this._alive) return
        const devCode = res && res.devCode ? res.devCode : ''
        this.setData({
          codeSent: true,
          devCode,
          code: devCode || this.data.code,
          cooldown: Number(res && res.cooldown) || 60,
        })
        this.startCooldown()
      })
      .catch((error) => {
        if (!this._alive) return
        if (error && error.code === 'PHONE_ALREADY_BOUND') {
          wx.showToast({ title: '该微信已绑定手机号', icon: 'none' })
          this.leave()
          return
        }
        this.setData({ error: format.errorText(error) })
      })
      .then(() => {
        if (this._alive) this.setData({ sending: false })
      })
  },

  startCooldown() {
    this.stopCooldown()
    this.cooldownTimer = setInterval(() => {
      const next = this.data.cooldown - 1
      if (next <= 0) {
        this.stopCooldown()
        this.setData({ cooldown: 0 })
      } else {
        this.setData({ cooldown: next })
      }
    }, 1000)
  },

  stopCooldown() {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer)
      this.cooldownTimer = null
    }
  },

  verify() {
    if (this.data.verifying) return
    const phone = String(this.data.phone || '').trim()
    const code = String(this.data.code || '').trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      this.setData({ error: '请输入正确的 11 位手机号。' })
      return
    }
    if (!/^\d{6}$/.test(code)) {
      this.setData({ error: '请输入 6 位验证码。' })
      return
    }
    this.setData({ verifying: true, error: '' })
    api.auth
      .linkPhone({ phone, code })
      .then((result) => {
        if (!this._alive) return
        auth.applyMember(result.member, result.token)
        wx.showToast({ title: '手机号已绑定', icon: 'success' })
        setTimeout(() => this.leave(), 450)
      })
      .catch((error) => {
        if (!this._alive) return
        if (error && error.code === 'PHONE_ALREADY_BOUND') {
          wx.showToast({ title: '该微信已绑定手机号', icon: 'none' })
          this.leave()
          return
        }
        this.setData({ error: format.errorText(error) })
      })
      .then(() => {
        if (this._alive) this.setData({ verifying: false })
      })
  },

  leave() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },
})
