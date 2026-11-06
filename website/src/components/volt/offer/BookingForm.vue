<template>
  <section class="offer-form-panel" :class="{ 'is-on': active }" aria-label="预约表单">
    <header class="form-head">
      <div>
        <p class="form-no mono">EXECUTE / 执行预约</p>
        <h3>预留一个位置<em>。</em></h3>
      </div>
      <div class="form-chosen">
        <span class="mono">已选 {{ selectedPlan.code }}</span>
        <b>{{ selectedPlan.name }}</b>
        <strong>¥{{ selectedPlan.price }}</strong>
      </div>
    </header>

    <form class="booking-form" novalidate @submit.prevent="onSubmit">
      <div class="field-row">
        <div class="field">
          <label for="offer-name"><span class="mono">01</span>姓名</label>
          <input
            id="offer-name"
            v-model.trim="form.name"
            class="field-input"
            :class="{ error: errors.name }"
            type="text"
            maxlength="24"
            autocomplete="name"
            placeholder="怎么称呼你"
            :aria-invalid="Boolean(errors.name)"
            @input="errors.name = ''"
          />
          <p v-if="errors.name" class="err mono">{{ errors.name }}</p>
        </div>

        <div class="field">
          <label for="offer-phone"><span class="mono">02</span>手机号</label>
          <input
            id="offer-phone"
            :value="form.phone"
            class="field-input"
            :class="{ error: errors.phone }"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            autocomplete="tel"
            placeholder="11 位手机号"
            :aria-invalid="Boolean(errors.phone)"
            @input="onPhoneInput"
          />
          <p v-if="errors.phone" class="err mono">{{ errors.phone }}</p>
        </div>
      </div>

      <div class="field otp-field">
        <label for="offer-code"><span class="mono">03</span>手机验证码</label>
        <div class="otp-row">
          <input
            id="offer-code"
            v-model="code"
            class="field-input"
            type="text"
            inputmode="numeric"
            maxlength="6"
            placeholder="6 位验证码"
            @input="onCodeInput"
          />
          <button
            type="button"
            class="otp-send"
            :disabled="sending || otpCooldown > 0"
            @click="sendCode"
          >
            {{ otpCooldown > 0 ? `${otpCooldown}s` : sending ? '发送中…' : '发送验证码' }}
          </button>
        </div>
        <button
          v-if="codeSent && !isVerified"
          type="button"
          class="otp-verify"
          :disabled="verifying"
          @click="verifyCode"
        >
          {{ verifying ? '验证中…' : '确认验证手机号' }}
        </button>
        <p v-if="devCode" class="otp-dev mono">本地联调验证码：{{ devCode }}</p>
        <p v-if="otpError" class="err mono">{{ otpError }}</p>
        <p v-else-if="isVerified" class="otp-ok mono">手机号已验证，可提交体验课预约</p>
      </div>

      <div class="field-row">
        <div class="field">
          <label for="offer-venue"><span class="mono">04</span>充电站</label>
          <select
            id="offer-venue"
            v-model="form.venue"
            class="field-input"
            :class="{ error: errors.venue }"
            @change="errors.venue = ''"
          >
            <option value="" disabled>选择门店</option>
            <option v-for="venue in venues" :key="venue.code" :value="venue.code">
              {{ venue.name }}
            </option>
          </select>
          <p v-if="errors.venue" class="err mono">{{ errors.venue }}</p>
        </div>

        <div class="field">
          <label for="offer-date"><span class="mono">05</span>到场日期</label>
          <input
            id="offer-date"
            v-model="form.date"
            class="field-input"
            :class="{ error: errors.date }"
            type="date"
            :min="today"
            :max="maxDate"
            :aria-invalid="Boolean(errors.date)"
            @change="errors.date = ''"
          />
          <p v-if="errors.date" class="err mono">{{ errors.date }}</p>
        </div>
      </div>

      <SessionPicker
        :sessions="availability?.sessions || []"
        :selected="form.sessionId"
        :loading="availabilityLoading"
        :loaded="Boolean(availability)"
        @select="selectSession"
      />
      <p v-if="errors.session" class="err mono">{{ errors.session }}</p>

      <div
        class="slot-line"
        :class="{ 'is-full': availability?.soldOut, 'is-loading': availabilityLoading }"
        aria-live="polite"
      >
        <span class="slot-kicker mono">AVAILABILITY</span>
        <template v-if="availability">
          <span class="slot-copy">
            {{ formatCnDate(availability.date) }} · {{ availability.venueName }} · 首次体验课
          </span>
          <b class="slot-count mono">
            {{ availability.soldOut ? '已满' : `余 ${availability.remaining} 位` }}
          </b>
        </template>
        <span v-else-if="availabilityLoading" class="slot-copy">正在查余位…</span>
        <span v-else-if="availabilityError" class="slot-copy">{{ availabilityError }}</span>
        <span v-else class="slot-copy">选好门店和日期，这里显示体验课时段</span>
      </div>

      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

      <button
        ref="submitBtn"
        type="submit"
        class="execute-btn"
        :disabled="loading || availability?.soldOut || cooldown > 0"
      >
        <span>{{ submitLabel }}</span>
        <span class="submit-price mono">¥{{ selectedPlan.price }}</span>
        <b aria-hidden="true">→</b>
      </button>
      <p class="privacy mono">提交即代表同意门店服务条款，信息仅用于本次预约。</p>
    </form>

    <div class="form-foot mono">
      <span>体验课到店付款</span>
      <span>无推销</span>
      <span>三店通用</span>
    </div>

    <BookingSuccessModal
      v-if="confirmed && order"
      :booking="order"
      @close="handleReset"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import BookingSuccessModal from './BookingSuccessModal.vue'
import SessionPicker from './SessionPicker.vue'
import { venues } from '../../../data/site.js'

import { formatCnDate } from '../../../utils/date.js'
import { isValidPhone } from '../../../utils/phone.js'
import { useBooking } from '../../../composables/useBooking.js'
import { useCooldown } from '../../../composables/useCooldown.js'
import {
  ApiError,
  saveWebSession,
  sendOtp,
  verifyOtp,
} from '../../../services/api/index.js'

const props = defineProps({
  active: { type: Boolean, default: false },
  preset: { type: Object, default: () => ({ program: '', venue: '', plan: '' }) },
  selectedPlan: { type: Object, required: true },
})

const submitBtn = ref(null)
const code = ref('')
const codeSent = ref(false)
const devCode = ref('')
const otpError = ref('')
const sending = ref(false)
const verifying = ref(false)
const verifiedPhone = ref('')
const allowDevCode = import.meta.env.DEV
const { remaining: otpCooldown, start: startOtpCooldown } = useCooldown()

const {
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
  submitLabel,
  today,
  maxDate,
  submit,
  reset,
  onPhoneInput,
} = useBooking({
  preset: () => props.preset,
  active: () => props.active,
  selectedPlan: () => props.selectedPlan,
  submitButton: submitBtn,
})

const isVerified = computed(
  () => Boolean(form.phone) && verifiedPhone.value === form.phone
)

watch(
  () => form.phone,
  () => {
    verifiedPhone.value = ''
    code.value = ''
    codeSent.value = false
    devCode.value = ''
    otpError.value = ''
  }
)

function onCodeInput(event) {
  code.value = String(event.target.value || '').replace(/\D/g, '').slice(0, 6)
  otpError.value = ''
}

function sendCode() {
  if (sending.value || otpCooldown.value > 0) return
  if (!isValidPhone(form.phone)) {
    otpError.value = '请先填写正确的 11 位手机号。'
    return
  }
  sending.value = true
  otpError.value = ''
  sendOtp(form.phone)
    .then((res) => {
      codeSent.value = true
      devCode.value = allowDevCode ? res?.devCode || '' : ''
      if (devCode.value) code.value = devCode.value
      startOtpCooldown(res?.cooldown || 60)
    })
    .catch((error) => {
      otpError.value =
        error instanceof ApiError ? error.message : '验证码发送失败，请稍后重试。'
    })
    .finally(() => {
      sending.value = false
    })
}

function verifyCode() {
  if (verifying.value) return
  if (!/^\d{6}$/.test(String(code.value || '').trim())) {
    otpError.value = '请输入 6 位验证码。'
    return
  }
  verifying.value = true
  otpError.value = ''
  verifyOtp(form.phone, String(code.value).trim())
    .then((res) => {
      if (!res?.token || !res?.member) throw new Error('登录响应缺少会员信息')
      saveWebSession({ token: res.token, member: res.member, phone: form.phone })
      verifiedPhone.value = form.phone
      codeSent.value = false
    })
    .catch((error) => {
      otpError.value =
        error instanceof ApiError ? error.message : '验证失败，请稍后重试。'
    })
    .finally(() => {
      verifying.value = false
    })
}

function onSubmit() {
  if (!isVerified.value) {
    otpError.value = '请先验证手机号，再提交体验课预约。'
    return
  }
  submit()
}

function selectSession(session) {
  form.sessionId = session.id
  errors.session = ''
}

function handleReset() {
  reset()
  code.value = ''
  codeSent.value = false
  devCode.value = ''
  otpError.value = ''
  verifiedPhone.value = ''
}
</script>

<style scoped>
.offer-form-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: clamp(16px, 2.2vh, 24px);
  border: 1px solid color-mix(in srgb, #0D0B08 14%, transparent);
  background: var(--paper);
  color: var(--ink);
  opacity: 0;
  transform: translateY(16px);
  transition: opacity .5s ease .16s, transform .7s var(--ease-gate) .16s;
}
.offer-form-panel.is-on { opacity: 1; transform: none; }

.form-head {
  flex: none;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid color-mix(in srgb, #0D0B08 14%, transparent);
}
.form-no {
  margin: 0;
  font-size: 9px;
  letter-spacing: .12em;
  color: #8A5200;
}
.form-head h3 {
  margin: 5px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(26px, 2.4vw, 34px);
  font-weight: 900;
  line-height: 1.03;
  letter-spacing: -.05em;
}
.form-head h3 em { font-style: normal; color: var(--acid); }
.form-chosen {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  text-align: right;
}
.form-chosen span {
  font-size: 7.5px;
  letter-spacing: .06em;
  color: color-mix(in srgb, #0D0B08 74%, transparent);
}
.form-chosen b {
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
  color: var(--ink);
}
.form-chosen strong {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 400;
  line-height: 1;
  color: #8A5200;
}

.booking-form {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 11px;
  padding-top: clamp(16px, 2.4vh, 22px);
}
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.field label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
  color: color-mix(in srgb, #0D0B08 76%, transparent);
}
.field label .mono {
  font-size: 7.5px;
  letter-spacing: .06em;
  color: #8A5200;
}

.field-input {
  width: 100%;
  min-height: 40px;
  padding: 7px 10px;
  border: 1px solid color-mix(in srgb, #0D0B08 26%, transparent);
  border-radius: 0;
  background: #ffffff;
  color: #0D0B08;
  font-family: var(--font-cn);
  font-size: 12.5px;
  transition: border-color .18s ease, background .18s ease;
}
.field-input::placeholder { color: color-mix(in srgb, #0D0B08 40%, transparent); }
.field-input:focus {
  outline: none;
  border-color: var(--acid);
  background: #FFFDF6;
}
.field-input[readonly] {
  cursor: not-allowed;
  border-color: color-mix(in srgb, #0D0B08 14%, transparent);
  background: #F2E8D7;
  color: #5C4A32;
}
.field-input[readonly]:focus {
  border-color: color-mix(in srgb, #0D0B08 14%, transparent);
  background: #F2E8D7;
}
.field-input.error {
  border-color: var(--acid);
  background: #FCEBDD;
}
select.field-input {
  appearance: none;
  background-image:
    linear-gradient(45deg, transparent 50%, var(--acid) 50%),
    linear-gradient(135deg, var(--acid) 50%, transparent 50%);
  background-position: calc(100% - 13px) 17px, calc(100% - 8px) 17px;
  background-size: 5px 5px, 5px 5px;
  background-repeat: no-repeat;
}
select.field-input option { background: #ffffff; color: #0D0B08; }
input[type='date'].field-input { color-scheme: light; }
.err {
  margin: 0;
  font-size: 10px;
  line-height: 1.4;
  letter-spacing: .02em;
  color: #8A3B00;
}

.otp-row {
  display: flex;
  gap: 8px;
}

.otp-row .field-input {
  flex: 1;
  min-width: 0;
}

.otp-send {
  flex: none;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid #0D0B08;
  border-radius: 0;
  background: #0D0B08;
  color: var(--paper);
  font-family: var(--font-cn);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

.otp-send:disabled {
  opacity: .45;
  cursor: default;
}

.otp-verify {
  align-self: flex-start;
  margin-top: 2px;
  padding: 6px 10px;
  border: 1px solid var(--acid);
  border-radius: 0;
  background: transparent;
  color: #8A5200;
  font-family: var(--font-cn);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

.otp-dev {
  margin: 0;
  font-size: 9px;
  letter-spacing: .02em;
  color: #8A5200;
}

.otp-ok {
  margin: 0;
  font-size: 10px;
  letter-spacing: .02em;
  color: #2F6B3A;
}

.slot-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 26px;
  padding: 5px 9px;
  border: 1px dashed color-mix(in srgb, #0D0B08 22%, transparent);
  background: color-mix(in srgb, #F59E0B 5%, transparent);
  font-size: 9px;
  color: color-mix(in srgb, #0D0B08 72%, transparent);
}
.slot-kicker {
  flex: none;
  font-size: 7px;
  letter-spacing: .08em;
  color: #8A5200;
}
.slot-copy {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-cn);
}
.slot-count {
  flex: none;
  margin-left: auto;
  font-size: 9px;
  color: #8A5200;
}
.slot-line.is-full {
  border-color: color-mix(in srgb, #0D0B08 34%, transparent);
  background: color-mix(in srgb, #0D0B08 6%, transparent);
}
.slot-line.is-full .slot-count { color: var(--ink); }
.slot-line.is-loading .slot-copy { color: color-mix(in srgb, #0D0B08 66%, transparent); }

.form-error {
  margin: 0;
  padding: 6px 9px;
  border-left: 3px solid var(--acid);
  background: color-mix(in srgb, #F59E0B 12%, transparent);
  font-family: var(--font-cn);
  font-size: 10px;
  line-height: 1.45;
  color: #7A3B00;
}

.execute-btn {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  margin-top: 2px;
  padding: 10px 15px;
  background: var(--acid);
  color: var(--ink);
  font-family: var(--font-cn);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: .03em;
  transition: background .2s ease, color .2s ease;
}
.execute-btn .submit-price {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 500;
  letter-spacing: .04em;
}
.execute-btn b { font-size: 18px; line-height: 1; transition: transform .2s ease; }
.execute-btn:hover { background: var(--ink); color: var(--acid); }
.execute-btn:hover b { transform: translateX(3px); }
.execute-btn:disabled { opacity: .65; cursor: wait; }

.privacy {
  margin: 0;
  font-size: 8px;
  line-height: 1.5;
  letter-spacing: .02em;
  color: color-mix(in srgb, #0D0B08 70%, transparent);
}

.form-foot {
  flex: none;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid color-mix(in srgb, #0D0B08 12%, transparent);
  font-size: 8px;
  letter-spacing: .1em;
  color: color-mix(in srgb, #0D0B08 68%, transparent);
}

@media (max-height: 800px) and (min-width: 901px) {
  .booking-form { gap: 8px; padding-top: 10px; }
  .field-input { min-height: 37px; }
  .slot-line { min-height: 24px; padding: 4px 8px; }
  .execute-btn { min-height: 44px; }
}

@media (max-width: 900px) {
  .offer-form-panel { flex: none; padding: 13px; }
  .form-head { flex-wrap: wrap; gap: 8px; padding-bottom: 9px; }
  .form-head h3 { font-size: 26px; }
  .form-chosen {
    flex-direction: row;
    align-items: baseline;
    gap: 7px;
    width: 100%;
    justify-content: flex-start;
  }
  .form-chosen strong { margin-left: auto; font-size: 19px; }
  .booking-form { gap: 9px; padding-top: 10px; justify-content: flex-start; }
  .field-row { gap: 8px; }
  .field label { font-size: 10.5px; }
  .field-input { min-height: 38px; font-size: 12px; }
  select.field-input { background-position: calc(100% - 12px) 16px, calc(100% - 7px) 16px; }
  .execute-btn { min-height: 45px; font-size: 12px; }
  .slot-kicker { display: none; }
  .slot-line { font-size: 9px; padding: 5px 8px; }
  .privacy { font-size: 7px; }
  .form-foot { margin-top: 12px; font-size: 7px; }
}
</style>
