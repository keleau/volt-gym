import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  ApiError,
  BOOKING_WINDOW_DAYS,
  createBooking,
  getAvailability,
} from '../services/api/index.js'
import { clearSelection } from '../services/selection.js'
import { addDaysIso, isBeyondDays, isPastDate, todayIso } from '../utils/date.js'
import { isMemberBlocked } from '../utils/member.js'
import { isValidPhone, normalizePhone } from '../utils/phone.js'
import { useCooldown } from './useCooldown.js'

export function useBooking({
  preset,
  active,
  selectedPlan,
  memberSession,
  submitButton,
  onBooked,
}) {
  const today = todayIso()
  const maxDate = addDaysIso(BOOKING_WINDOW_DAYS)
  const form = reactive({ name: '', phone: '', venue: '', program: '', date: '', sessionId: '' })
  const errors = reactive({ name: '', phone: '', plan: '', venue: '', program: '', date: '' })
  const loading = ref(false)
  const confirmed = ref(false)
  const order = ref(null)
  const formError = ref('')
  const availability = ref(null)
  const availabilityError = ref('')
  const availabilityLoading = ref(false)
  const { remaining: cooldown, start: startCooldown, stop: stopCooldown } = useCooldown()
  let availabilityToken = 0
  let lastFocused = null

  const memberBlocked = computed(() => isMemberBlocked(memberSession.value?.member))

  const submitLabel = computed(() => {
    if (loading.value) return '正在接入…'
    if (cooldown.value > 0) return `请等待 ${cooldown.value} 秒`
    if (availability.value?.soldOut) return '该场已满 · 换一天'
    return '确认预约'
  })

  watch(
    () => [preset().plan, preset().program, preset().venue],
    ([plan, program, venue]) => {
      if (program) {
        form.program = program
        errors.program = ''
      }
      if (venue) {
        form.venue = venue
        errors.venue = ''
      }
      if (plan || program || venue) formError.value = ''
    }
  )

  watch(
    () => [form.venue, form.program, form.date],
    () => {
      form.sessionId = ''
      loadAvailability()
    }
  )

  watch(
    () => [form.name, form.phone, form.venue, form.program, form.date, form.sessionId, selectedPlan().code],
    () => {
      formError.value = ''
    }
  )

  watch(
    () => active(),
    (visible) => {
      if (visible) loadAvailability()
    }
  )

  async function loadAvailability() {
    const token = ++availabilityToken
    availability.value = null
    availabilityError.value = ''
    const { venue, program, date } = form
    if (!venue || !program || !date) {
      availabilityLoading.value = false
      return
    }
    if (isPastDate(date) || isBeyondDays(date, BOOKING_WINDOW_DAYS)) {
      availabilityLoading.value = false
      return
    }
    availabilityLoading.value = true
    try {
      const data = await getAvailability({ venueCode: venue, programNo: program, date })
      if (token === availabilityToken) {
        availability.value = data
        const openSessions = (data.sessions || []).filter(
          (session) => !session.soldOut && Number(session.left) > 0
        )
        if (!openSessions.some((session) => session.id === form.sessionId)) {
          form.sessionId = openSessions[0]?.id || ''
        }
      }
    } catch (error) {
      if (token === availabilityToken) {
        availability.value = null
        availabilityError.value =
          error instanceof ApiError ? error.message : '余位查询失败，请稍后重试'
      }
    } finally {
      if (token === availabilityToken) availabilityLoading.value = false
    }
  }

  function onPhoneInput(event) {
    const digits = normalizePhone(event.target.value)
    form.phone = digits
    event.target.value = digits
    errors.phone = ''
  }

  function validate() {
    const asMember = Boolean(memberSession.value?.member)
    // 会员身份由 token 决定，旧版本地会话可能没有完整手机号，仍允许提交。
    errors.name = asMember || form.name ? '' : '请填写姓名'
    errors.phone = asMember || isValidPhone(form.phone) ? '' : '请填写正确的 11 位手机号'
    errors.plan = selectedPlan().code ? '' : '请选择次卡方案'
    errors.venue = form.venue ? '' : '请选择充电站'
    errors.program = form.program ? '' : '请选择课程'
    errors.date = !form.date
      ? '请选择到场日期'
      : isPastDate(form.date)
        ? '日期不能早于今天'
        : isBeyondDays(form.date, BOOKING_WINDOW_DAYS)
          ? `只能预约 ${BOOKING_WINDOW_DAYS} 天内的场次`
          : ''
    return !errors.name && !errors.phone && !errors.plan && !errors.venue && !errors.program && !errors.date
  }

  async function submit() {
    if (loading.value || confirmed.value || cooldown.value > 0) return
    formError.value = ''
    if (memberBlocked.value) {
      formError.value = '会员次卡余额不足或已过期，请先续卡。'
      return
    }
    if (!validate()) return

    lastFocused = submitButton.value
    loading.value = true
    try {
      const created = await createBooking({
        planCode: selectedPlan().code,
        name: form.name,
        phone: form.phone,
        venueCode: form.venue,
        programNo: form.program,
        date: form.date,
        sessionId: form.sessionId || undefined,
      })
      order.value = created
      confirmed.value = true
      onBooked?.()
      if (availability.value) {
        const bookedSessionId = form.sessionId || created.sessionId
        const sessions = availability.value.sessions?.map((session) => {
          if (session.id !== bookedSessionId) return session
          const left = Math.max(0, Number(session.left) - 1)
          return { ...session, booked: Number(session.booked) + 1, left, soldOut: left <= 0 }
        })
        const remaining = Math.max(0, Number(availability.value.remaining) - 1)
        availability.value = {
          ...availability.value,
          remaining,
          soldOut: remaining <= 0,
          ...(sessions ? { sessions } : {}),
        }
      }
    } catch (error) {
      if (error instanceof ApiError && error.fields) {
        for (const key of Object.keys(errors)) {
          if (error.fields[key]) errors[key] = error.fields[key]
        }
      }
      formError.value =
        error instanceof ApiError ? error.message : '预约失败，请稍后再试。'
      if (error instanceof ApiError && error.code === 'DATE_OUT_OF_RANGE') {
        errors.date = error.message
      }
      if (
        error instanceof ApiError &&
        ['SLOT_FULL', 'DUPLICATE_BOOKING', 'PAST_SESSION'].includes(error.code)
      ) {
        loadAvailability()
      }
      if (error instanceof ApiError && error.code === 'RATE_LIMITED') {
        startCooldown(5, () => {
          formError.value = ''
        })
      }
    } finally {
      loading.value = false
    }
  }

  function reset() {
    confirmed.value = false
    order.value = null
    formError.value = ''
    form.name = memberSession.value?.member?.nickname || ''
    form.phone = memberSession.value?.phone || ''
    form.venue = ''
    form.program = ''
    form.date = ''
    form.sessionId = ''
    Object.keys(errors).forEach((key) => {
      errors[key] = ''
    })
    availability.value = null
    availabilityError.value = ''
    availabilityLoading.value = false
    availabilityToken++
    stopCooldown()
    clearSelection()
    const target = lastFocused
    lastFocused = null
    nextTick(() => {
      if (target?.isConnected) target.focus()
    })
  }

  function applyMember(payload) {
    if (payload?.member) {
      form.name = payload.member.nickname || form.name
      form.phone = normalizePhone(payload.phone || form.phone)
      errors.name = ''
      errors.phone = ''
      return
    }
    form.name = ''
    form.phone = ''
  }

  return {
    form,
    errors,
    loading,
    confirmed,
    order,
    formError,
    availability,
    availabilityError,
    availabilityLoading,
    cooldown,
    memberBlocked,
    submitLabel,
    today,
    maxDate,
    submit,
    reset,
    applyMember,
    onPhoneInput,
  }
}
