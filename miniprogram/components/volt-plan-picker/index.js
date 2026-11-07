Component({
  properties: {
    plans: { type: Array, value: [] },
    currentCode: { type: String, value: '' },
  },

  methods: {
    onPick(event) {
      const code = event.currentTarget.dataset.code
      if (!code) return
      this.triggerEvent('select', { code })
    },
  },
})
