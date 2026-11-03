const COLORS = {
  line: '#66705B',
  lineWeight: '#1B1712',
  accent: '#A33B2B',
  grid: '#D9CBB6',
  text: '#72654F',
  area: 'rgba(102,112,91,0.16)',
  areaWeight: 'rgba(27,20,15,0.08)',
}

Component({
  properties: {
    mode: { type: String, value: 'sessions' },
    points: { type: Array, value: [] },
    height: { type: String, value: '420rpx' },
  },

  data: {
    empty: true,
  },

  observers: {
    'mode, points': function (mode, points) {
      this.setData({ empty: !(points || []).length })
      if (this._ctx) this.draw()
    },
  },

  lifetimes: {
    ready() {
      this.initCanvas()
    },
    detached() {
      this._detached = true
      this._ctx = null
      this._canvas = null
      if (this._initTimer) clearTimeout(this._initTimer)
    },
  },

  methods: {
    initCanvas() {
      if (this._detached) return
      wx.createSelectorQuery()
        .in(this)
        .select('#chart')
        .fields({ node: true, size: true })
        .exec((res) => {
          if (this._detached) return
          const target = res && res[0]
          if (!target || !target.node) {
            this._initTries = (this._initTries || 0) + 1
            if (this._initTries <= 30) {
              this._initTimer = setTimeout(() => this.initCanvas(), 120)
            }
            return
          }
          const canvas = target.node
          const width = target.width
          const height = target.height
          if (!width || !height) return
          const dpr = Math.min(2, (wx.getWindowInfo && wx.getWindowInfo().pixelRatio) || 1)
          canvas.width = width * dpr
          canvas.height = height * dpr
          const ctx = canvas.getContext('2d')
          ctx.scale(dpr, dpr)
          this._canvas = canvas
          this._ctx = ctx
          this._size = { width, height }
          this.draw()
        })
    },

    draw() {
      if (!this._ctx || !this._size) return
      const ctx = this._ctx
      const { width, height } = this._size
      const values = (this.data.points || []).map((item) => Number(item.value))
      const labels = (this.data.points || []).map((item) => String(item.label || ''))
      ctx.clearRect(0, 0, width, height)
      if (!values.length) return

      const isWeight = this.data.mode === 'weight'
      const isDuration = this.data.mode === 'duration'
      const lineColor = isWeight ? COLORS.lineWeight : COLORS.line
      const areaColor = isWeight ? COLORS.areaWeight : COLORS.area
      const pad = { left: 34, right: 14, top: 26, bottom: 26 }
      const plotW = Math.max(1, width - pad.left - pad.right)
      const plotH = Math.max(1, height - pad.top - pad.bottom)

      let min = Math.min(...values)
      let max = Math.max(...values)
      if (isWeight) {
        const span = Math.max(0.6, max - min)
        min -= span * 0.25
        max += span * 0.25
      } else {
        min = 0
        max = Math.max(1, max)
      }
      if (max === min) max = min + 1

      const xAt = (index) =>
        pad.left + (values.length === 1 ? plotW / 2 : (index / (values.length - 1)) * plotW)
      const yAt = (value) => pad.top + (1 - (value - min) / (max - min)) * plotH

      ctx.font = '10px sans-serif'
      ctx.fillStyle = COLORS.text
      ctx.textAlign = 'right'
      ctx.textBaseline = 'middle'
      for (let tick = 0; tick <= 3; tick += 1) {
        const value = min + ((max - min) * tick) / 3
        const y = yAt(value)
        ctx.strokeStyle = COLORS.grid
        ctx.setLineDash([4, 4])
        ctx.beginPath()
        ctx.moveTo(pad.left, y)
        ctx.lineTo(width - pad.right, y)
        ctx.stroke()
        ctx.setLineDash([])
        const text = isWeight
          ? `${value.toFixed(1)}`
          : `${Math.round(value)}${isDuration ? '′' : ''}`
        ctx.fillText(text, pad.left - 7, y)
      }

      const step = Math.max(1, Math.ceil(values.length / 6))
      ctx.textAlign = 'center'
      ctx.textBaseline = 'top'
      labels.forEach((label, index) => {
        if (index % step !== 0 && index !== labels.length - 1) return
        ctx.fillText(label, xAt(index), height - pad.bottom + 8)
      })

      const fill = ctx.createLinearGradient(0, pad.top, 0, height - pad.bottom)
      fill.addColorStop(0, areaColor)
      fill.addColorStop(1, 'rgba(102,112,91,0)')
      ctx.beginPath()
      values.forEach((value, index) => {
        const x = xAt(index)
        const y = yAt(value)
        if (index === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.lineTo(xAt(values.length - 1), height - pad.bottom)
      ctx.lineTo(xAt(0), height - pad.bottom)
      ctx.closePath()
      ctx.fillStyle = fill
      ctx.fill()

      ctx.beginPath()
      values.forEach((value, index) => {
        const x = xAt(index)
        const y = yAt(value)
        if (index === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.strokeStyle = lineColor
      ctx.lineWidth = 2
      ctx.stroke()

      values.forEach((value, index) => {
        const x = xAt(index)
        const y = yAt(value)
        const last = index === values.length - 1
        ctx.beginPath()
        ctx.arc(x, y, last ? 4.5 : 3, 0, Math.PI * 2)
        ctx.fillStyle = last ? COLORS.accent : lineColor
        ctx.fill()
        ctx.strokeStyle = '#FDF9F0'
        ctx.lineWidth = 2
        ctx.stroke()
      })
    },
  },
})
