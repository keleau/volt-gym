<template>
  <section class="member-panel" :class="{ 'is-connected': Boolean(member), 'is-blocked': isBlocked }">
    <div class="member-top">
      <span class="member-kicker mono">MEMBER / 会员接入</span>
      <span class="member-state mono">{{ member ? 'CONNECTED' : 'SIGN IN' }}</span>
    </div>

    <template v-if="member">
      <div class="member-id-row">
        <div class="member-id">
          <b>{{ member.nickname || 'VOLT 会员' }}</b>
          <span class="mono">
            {{ member.planName || '按次充电' }} · 余 {{ member.credits }} 次 · 有效至 {{ member.expiresAt || '--' }}
          </span>
        </div>
        <button type="button" class="member-link mono" @click="logout">退出</button>
      </div>

      <p v-if="nextBooking" class="member-next mono">
        NEXT / {{ nextBooking.date }} {{ nextBooking.time }} · {{ nextBooking.programName }} · {{ nextBooking.venueName }}
      </p>
      <p v-if="error" class="member-error">{{ error }}</p>
      <p class="member-hint">
        {{ isBlocked ? '次卡余额不足或已过期，暂不能预约。' : '预约不扣次，到店核销时扣 1 次；官网和小程序共用同一份余额。' }}
      </p>
    </template>

    <template v-else>
      <p class="member-help">登录后，官网预约会记入你的次卡，并与小程序共用会员资料。</p>

      <div class="member-form">
        <input
          v-model="phone"
          class="member-input"
          type="tel"
          inputmode="numeric"
          maxlength="11"
          placeholder="会员手机号"
          @input="onPhone"
          @keyup.enter="sendCode"
        />
        <button type="button" class="member-btn" :disabled="sending || cooldown > 0" @click="sendCode">
          {{ cooldown > 0 ? `${cooldown}s` : sending ? '发送中…' : '发送验证码' }}
        </button>
      </div>

      <div v-if="codeSent" class="member-form">
        <input
          v-model="code"
          class="member-input"
          type="text"
          inputmode="numeric"
          maxlength="6"
          placeholder="6 位验证码"
          @keyup.enter="verify"
        />
        <button type="button" class="member-btn member-btn--solid" :disabled="verifying" @click="verify">
          {{ verifying ? '登录中…' : '登录会员' }}
        </button>
      </div>

      <p v-if="devCode" class="member-dev mono">本地联调验证码：{{ devCode }}</p>
      <p v-if="error" class="member-error">{{ error }}</p>
      <p class="member-hint">登录即认领同手机号的历史预约；短信通道未配置时使用联调验证码。</p>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ApiError,
  clearWebSession,
  getMemberHome,
  getWebSession,
  saveWebSession,
  sendOtp,
  verifyOtp,
} from '../../../services/api/index.js'
import { useCooldown } from '../../../composables/useCooldown.js'
import { isMemberBlocked } from '../../../utils/member.js'
import { isValidPhone, normalizePhone } from '../../../utils/phone.js'

const emit = defineEmits(['change'])
const member = ref(null)
const nextBooking = ref(null)
const phone = ref('')
const code = ref('')
const devCode = ref('')
const codeSent = ref(false)
const sending = ref(false)
const verifying = ref(false)
const { remaining: cooldown, start: startCooldown } = useCooldown({ intervalMs: 1000 })
const error = ref('')
const allowDevCode = import.meta.env.DEV
let suppressExpired = false

const isBlocked = computed(() => isMemberBlocked(member.value))

function emitChange() {
  emit('change', { member: member.value, phone: phone.value })
}

function applySession(session) {
  member.value = session?.member || null
  phone.value = session?.phone || phone.value
  emitChange()
}

function resetMember(message = '') {
  member.value = null
  nextBooking.value = null
  codeSent.value = false
  code.value = ''
  error.value = message
  emitChange()
}

function onExpired() {
  if (suppressExpired) return
  resetMember('登录已过期，请重新登录。')
}

function onPhone(event) {
  phone.value = normalizePhone(event.target.value)
  event.target.value = phone.value
  error.value = ''
}

function sendCode() {
  if (sending.value || cooldown.value > 0) return
  const value = normalizePhone(phone.value)
  if (!isValidPhone(value)) {
    error.value = '请输入正确的 11 位手机号。'
    return
  }
  error.value = ''
  sending.value = true
  sendOtp(value)
    .then((res) => {
      codeSent.value = true
      devCode.value = allowDevCode ? res?.devCode || '' : ''
      if (devCode.value) code.value = devCode.value
      startCooldown(res?.cooldown || 60)
    })
    .catch((err) => {
      error.value = err instanceof ApiError ? err.message : '验证码发送失败，请稍后重试。'
    })
    .finally(() => {
      sending.value = false
    })
}

function verify() {
  if (verifying.value) return
  if (!/^\d{6}$/.test(String(code.value || '').trim())) {
    error.value = '请输入 6 位验证码。'
    return
  }
  error.value = ''
  verifying.value = true
  verifyOtp(String(phone.value).trim(), String(code.value).trim())
    .then((res) => {
      if (!res?.token || !res?.member) throw new Error('登录响应缺少会员信息')
      const session = { token: res.token, member: res.member, phone: phone.value }
      saveWebSession(session)
      applySession(session)
      codeSent.value = false
      return refresh()
    })
    .catch((err) => {
      error.value = err instanceof ApiError ? err.message : '登录失败，请稍后重试。'
    })
    .finally(() => {
      verifying.value = false
    })
}

function refresh() {
  const session = getWebSession()
  if (!session?.token) return Promise.resolve()
  return getMemberHome()
    .then((home) => {
      if (!home?.member) return
      member.value = home.member
      nextBooking.value = home.nextBooking || null
      saveWebSession({ ...session, member: home.member, phone: session.phone || phone.value })
      emitChange()
    })
    .catch((err) => {
      if (err instanceof ApiError && err.code === 'UNAUTHORIZED') {
        resetMember('登录已过期，请重新登录。')
        return
      }
      error.value = err instanceof ApiError ? err.message : '会员信息加载失败。'
    })
}

function logout() {
  suppressExpired = true
  clearWebSession()
  resetMember()
  suppressExpired = false
}

onMounted(() => {
  window.addEventListener('volt:session-expired', onExpired)
  const session = getWebSession()
  if (session?.token) {
    phone.value = session.phone || ''
    member.value = session.member || null
    nextBooking.value = null
    emitChange()
    refresh()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('volt:session-expired', onExpired)
})

defineExpose({ refresh })
</script>

<style scoped>
.member-panel {
  flex: none;
  margin-bottom: 8px;
  padding: 10px 11px;
  border: 1px solid color-mix(in srgb, #0D0B08 18%, transparent);
  background: color-mix(in srgb, #F59E0B 5%, transparent);
}
.member-panel.is-connected {
  border-color: color-mix(in srgb, #0D0B08 30%, transparent);
  background: color-mix(in srgb, #0D0B08 3.5%, transparent);
}
.member-panel.is-blocked {
  border-color: color-mix(in srgb, #8A3B00 44%, transparent);
  background: color-mix(in srgb, #F59E0B 11%, transparent);
}
.member-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.member-kicker {
  font-size: 8px;
  letter-spacing: .09em;
  color: #8A5200;
}
.member-state {
  flex: none;
  font-size: 7px;
  letter-spacing: .08em;
  color: color-mix(in srgb, #0D0B08 58%, transparent);
}
.member-help {
  margin: 6px 0 0;
  font-family: var(--font-cn);
  font-size: 10px;
  line-height: 1.5;
  color: color-mix(in srgb, #0D0B08 70%, transparent);
}
.member-id-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-top: 7px;
}
.member-id {
  min-width: 0;
}
.member-id b {
  display: block;
  overflow: hidden;
  font-family: var(--font-cn);
  font-size: 14px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-id span {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  font-size: 8px;
  color: #8A5200;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-next {
  margin: 7px 0 0;
  padding-top: 6px;
  overflow: hidden;
  border-top: 1px dashed color-mix(in srgb, #0D0B08 18%, transparent);
  font-size: 8px;
  color: color-mix(in srgb, #0D0B08 66%, transparent);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-link {
  flex: none;
  padding: 0;
  border: 0;
  border-bottom: 1px solid color-mix(in srgb, #8A5200 36%, transparent);
  background: transparent;
  color: #8A5200;
  font-size: 8px;
  cursor: pointer;
}
.member-form {
  display: flex;
  gap: 7px;
  margin-top: 7px;
}
.member-input {
  flex: 1;
  min-width: 0;
  height: 34px;
  padding: 0 9px;
  border: 1px solid color-mix(in srgb, #0D0B08 24%, transparent);
  border-radius: 0;
  background: #ffffff;
  color: #0D0B08;
  font-family: var(--font-cn);
  font-size: 11px;
}
.member-input::placeholder {
  color: color-mix(in srgb, #0D0B08 40%, transparent);
}
.member-input:focus {
  outline: none;
  border-color: var(--acid);
  background: #FFFDF6;
}
.member-btn {
  flex: none;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #8A5200;
  border-radius: 0;
  background: transparent;
  color: #8A5200;
  font-family: var(--font-cn);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}
.member-btn--solid {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}
.member-btn:disabled {
  opacity: .55;
  cursor: default;
}
.member-dev {
  margin: 6px 0 0;
  font-size: 8px;
  color: #8A5200;
}
.member-error {
  margin: 6px 0 0;
  font-size: 9px;
  line-height: 1.4;
  color: #8A3B00;
}
.member-hint {
  margin: 6px 0 0;
  font-size: 8px;
  line-height: 1.4;
  color: color-mix(in srgb, #0D0B08 54%, transparent);
}
.is-blocked .member-hint {
  color: #8A3B00;
}
</style>
