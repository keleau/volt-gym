Component({
  properties: {
    visible: { type: Boolean, value: false },
    eyebrow: { type: String, value: '' },
    title: { type: String, value: '' },
    subtitle: { type: String, value: '' },
    closable: { type: Boolean, value: true },
    closeOnMask: { type: Boolean, value: true },
    maxHeight: { type: String, value: '76vh' },
    zIndex: { type: Number, value: 300 },
  },

  data: {
    rendered: false,
    active: false,
  },

  observers: {
    visible(value) {
      if (this._renderTimer) clearTimeout(this._renderTimer)
      if (value) {
        this.setData({ rendered: true })
        this._renderTimer = setTimeout(() => this.setData({ active: true }), 20)
      } else if (this.data.rendered) {
        this.setData({ active: false })
        this._renderTimer = setTimeout(() => this.setData({ rendered: false }), 240)
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
    close() {
      this.triggerEvent('close')
    },
    onMask() {
      if (this.data.closeOnMask) this.close()
    },
    noop() {},
  },
})
