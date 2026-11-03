const auth = require('../../services/auth')
const format = require('../../utils/format')

Component({
  properties: {
    visible: { type: Boolean, value: false },
    closable: { type: Boolean, value: true },
  },

  data: {
    rendered: false,
    active: false,
    loading: false,
    nickname: '',
    avatarUrl: '',
  },

  observers: {
    visible(value) {
      if (this._renderTimer) clearTimeout(this._renderTimer)
      if (value) {
        this.setData({ rendered: true })
        this._renderTimer = setTimeout(() => this.setData({ active: true }), 20)
      } else if (this.data.rendered) {
        this.setData({ active: false })
        this._renderTimer = setTimeout(() => this.setData({ rendered: false }), 260)
      }
    },
  },

  lifetimes: {
    detached() {
      if (this._renderTimer) clearTimeout(this._renderTimer)
      this._renderTimer = null
    },
  },

  methods: {
    onMask() {
      if (this.data.closable) this.triggerEvent('cancel')
    },

    onCancel() {
      if (!this.data.loading) this.triggerEvent('cancel')
    },

    onNickname(event) {
      this.setData({ nickname: event.detail.value })
    },

    onChooseAvatar(event) {
      const url = event.detail && event.detail.avatarUrl
      if (url) this.setData({ avatarUrl: url })
    },

    onLogin() {
      if (this.data.loading) return
      this.setData({ loading: true })
      const profile = {}
      const nickname = String(this.data.nickname || '').trim()
      if (nickname) profile.nickname = nickname
      if (this.data.avatarUrl) profile.avatarUrl = this.data.avatarUrl
      auth
        .loginWithWechat(profile)
        .then((session) => {
          this.setData({ loading: false })
          wx.showToast({ title: '微信已接入', icon: 'success' })
          this.triggerEvent('success', { session, user: session.user })
        })
        .catch((error) => {
          this.setData({ loading: false })
          wx.showToast({ title: format.errorText(error), icon: 'none' })
        })
    },

    noop() {},
  },
})
