const api = require('../../services/api')
const auth = require('../../services/auth')
const dateUtil = require('../../utils/date')
const format = require('../../utils/format')

const FORM_META = {
  eyebrow: 'MEMBER CHECK / 会员识别',
  title: '验证手机号，识别身份。',
  subtitle: '绑定后自动同步体验课预约、官网购卡和会员卡。',
}

function digits(value, maxLength) {
  return String(value || '').replace(/\D/g, '').slice(0, maxLength)
}

Component({
  properties: {
    visible: { type: Boolean, value: false },
  },

  data: {
    stage: 'form',
    sheetEyebrow: FORM_META.eyebrow,
    sheetTitle: FORM_META.title,
    sheetSubtitle: FORM_META.subtitle,
    phone: '',
    code: '',
    codeSent: false,
    sending: false,
    verifying: false,
    cooldown: 0,
    devCode: '',
    error: '',
    result: null,
  },

  observers: {
    visible(value) {
      this._alive = true
      if (value) this.reset()
      else this.stopCooldown()
    },
  },

  lifetimes: {
    detached() {
      this._alive = false
      this.stopCooldown()
    },
  },

  methods: {
    reset() {
      this.stopCooldown()
      this.setData({
        stage: 'form',
        sheetEyebrow: FORM_META.eyebrow,
        sheetTitle: FORM_META.title,
        sheetSubtitle: FORM_META.subtitle,
        phone: '',
        code: '',
        codeSent: false,
        sending: false,
        verifying: false,
        cooldown: 0,
        devCode: '',
        error: '',
        result: null,
      })
    },

    onPhoneInput(event) {
      this.setData({ phone: digits(event.detail.value, 11), error: '' })
    },

    onCodeInput(event) {
      this.setData({ code: digits(event.detail.value, 6), error: '' })
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
            wx.showToast({ title: '当前微信已绑定手机号', icon: 'none' })
            this.triggerEvent('action', { action: 'close' })
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
        .then((res) => {
          if (!this._alive) return
          const member = res.member || {}
          const audience = res.audience || {}
          auth.applyMember(member, res.token, res.members, res.activeMemberId)
          const result = this.buildResult(member, audience, Boolean(res.merged))
          this.setData({
            stage: 'result',
            sheetEyebrow: result.eyebrow,
            sheetTitle: result.title,
            sheetSubtitle: result.subtitle,
            result,
          })
          this.triggerEvent('bound', {
            member,
            audience,
            merged: Boolean(res.merged),
          })
        })
        .catch((error) => {
          if (!this._alive) return
          if (error && error.code === 'PHONE_ALREADY_BOUND') {
            wx.showToast({ title: '当前微信已绑定手机号', icon: 'none' })
            this.triggerEvent('action', { action: 'close' })
            return
          }
          this.setData({ error: format.errorText(error) })
        })
        .then(() => {
          if (this._alive) this.setData({ verifying: false })
        })
    },

    buildResult(member, audience, merged) {
      const phone = audience.phone || member.phone || '已绑定'
      const hasCard = audience.hasCard != null
        ? Boolean(audience.hasCard)
        : Boolean(member.planCode) || Number(member.creditsTotal) > 0
      const hasTrial = Boolean(audience.hasTrial)
      const hasPending = Boolean(audience.hasPendingCard)
      const pendingOnly = !hasCard && !hasTrial && hasPending
      const planName = audience.planName || member.planName || ''
      const credits = Number(audience.credits != null ? audience.credits : member.credits) || 0
      const creditsTotal = Number(
        audience.creditsTotal != null ? audience.creditsTotal : member.creditsTotal,
      ) || 0
      const expiresAt = String(audience.expiresAt || member.expiresAt || '').replace(/-/g, '.')
      const trial = audience.trial || null
      const trialDate = trial && trial.date
        ? `${dateUtil.relativeDay(trial.date)} ${dateUtil.weekdayCn(trial.date)}`
        : ''
      const trialWhen = trial && trial.startTime ? `${trialDate} ${trial.startTime}` : ''
      const trialText = [trialWhen, trial && trial.programName].filter(Boolean).join(' · ')
      const pendingText = [
        audience.pendingPlanName || '购卡登记',
        audience.pendingCredits ? `${audience.pendingCredits} 次` : '',
      ].filter(Boolean).join(' · ')
      const mergedRow = merged ? { label: '官网记录', value: '已合并到当前会员' } : null
      const pendingRow = hasPending
        ? { label: '待开卡', value: `${pendingText} · 到店付款` }
        : null

      if (pendingOnly) {
        return {
          type: 'pending',
          stamp: 'WAIT',
          eyebrow: 'ORDER FOUND / 已登记',
          title: '购卡登记已找到。',
          subtitle: '到店付款后由店员开卡',
          lead: '你的购卡已经登记，到店付款并让店员确认开卡，次数会自动到账。',
          rows: [
            { label: '手机号', value: phone },
            { label: '购卡登记', value: pendingText },
            { label: '状态', value: '待前台开卡' },
          ],
          primary: { action: 'pass', label: '查看会员卡' },
          secondary: { action: 'trial', label: '预约体验课' },
          tertiary: { action: 'close', label: '先逛逛' },
        }
      }

      if (hasCard) {
        const rows = [
          { label: '手机号', value: phone },
          { label: '会员卡', value: planName || '已开卡' },
          { label: '剩余次数', value: `${credits} / ${creditsTotal} 次` },
          { label: '有效期', value: expiresAt || '长期有效' },
        ]
        if (hasTrial) rows.push({ label: '体验课', value: trialText || '已有预约' })
        if (pendingRow) rows.push(pendingRow)
        if (mergedRow) rows.push(mergedRow)
        return {
          type: 'card',
          stamp: 'PASS',
          eyebrow: 'CARD FOUND / 已识别',
          title: '已开卡，会员卡已接通。',
          subtitle: `已开卡 · ${planName || '会员卡'} · 余 ${credits} 次`,
          lead: '体验课、官网购卡和会员卡已经同步到这个微信。',
          rows,
          primary: { action: 'close', label: '进入会员中心' },
          secondary: { action: 'pass', label: '查看会员卡' },
          tertiary: null,
        }
      }

      if (hasTrial) {
        const checkedIn = trial && trial.status === 'checked-in'
        const trialStatus = checkedIn
          ? '已核销'
          : trial && trial.status === 'locked' ? '待确认' : '已预约'
        const rows = [
          { label: '手机号', value: phone },
          { label: '体验课', value: trialText || '已找到预约' },
          { label: '门店', value: (trial && trial.venueName) || '待确认' },
          { label: '状态', value: trialStatus },
        ]
        if (pendingRow) rows.push(pendingRow)
        if (mergedRow) rows.push(mergedRow)
        return {
          type: 'trial',
          stamp: 'TRIAL',
          eyebrow: 'TRIAL FOUND / 已识别',
          title: checkedIn ? '体验课已完成。' : '体验课已找到。',
          subtitle: trialText || '首次体验课',
          lead: checkedIn
            ? '这节体验课已经核销，接下来可以开卡继续练。'
            : '到店后出示预约号或扫码核销，本次不扣次卡。',
          rows,
          primary: { action: 'bookings', label: '查看我的预约' },
          secondary: { action: 'buy', label: '购买会员卡' },
          tertiary: { action: 'close', label: '先逛逛' },
        }
      }

      const rows = [
        { label: '手机号', value: phone },
        { label: '体验课', value: '暂无记录' },
        { label: '会员卡', value: '暂无记录' },
      ]
      if (pendingRow) rows.push(pendingRow)
      return {
        type: 'visitor',
        stamp: 'NEW',
        eyebrow: 'NEW CONNECTION / 新朋友',
        title: '先练一次，再说。',
        subtitle: '首次体验 ¥49 · 不办年卡',
        lead: '这个手机号还没有体验课或会员卡。先约一节体验课，或直接登记次卡。',
        rows,
        primary: { action: 'trial', label: '预约 49 元体验课' },
        secondary: { action: 'buy', label: '购买会员卡' },
        tertiary: { action: 'close', label: '先逛逛' },
      }
    },

    onAction(event) {
      const action = event.currentTarget.dataset.action
      if (!action) return
      this.triggerEvent('action', { action })
    },

    onClose() {
      if (this.data.stage !== 'result') return
      this.triggerEvent('action', { action: 'close' })
    },
  },
})
