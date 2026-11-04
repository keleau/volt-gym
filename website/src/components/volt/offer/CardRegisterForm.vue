<template>
  <section class="card-panel" :class="{ 'is-on': active }" aria-label="购卡登记">
    <header class="card-head">
      <div>
        <p class="card-no mono">CARD / 购卡登记</p>
        <h3>把卡记到<em>手机号</em>上。</h3>
      </div>
      <div class="card-chosen">
        <span class="mono">已选 {{ selectedPlan.code }}</span>
        <b>{{ selectedPlan.name }}</b>
        <strong>¥{{ selectedPlan.price }}</strong>
      </div>
    </header>

    <template v-if="!result">
      <p class="card-intro">
        提交后到店付款，店员确认开卡；开卡后用小程序同手机号登录，次数自动出现。
      </p>

      <form class="card-form" novalidate @submit.prevent="submit">
        <div class="field-row">
          <div class="field">
            <label for="card-name"><span class="mono">01</span>姓名</label>
            <input
              id="card-name"
              v-model.trim="form.name"
              class="field-input"
              :class="{ error: errors.name }"
              type="text"
              maxlength="24"
              autocomplete="name"
              placeholder="怎么称呼你"
              @input="errors.name = ''"
            />
            <p v-if="errors.name" class="err mono">{{ errors.name }}</p>
          </div>

          <div class="field">
            <label for="card-phone"><span class="mono">02</span>手机号</label>
            <input
              id="card-phone"
              :value="form.phone"
              class="field-input"
              :class="{ error: errors.phone }"
              type="tel"
              inputmode="numeric"
              maxlength="11"
              autocomplete="tel"
              placeholder="开卡手机号"
              @input="onPhoneInput"
            />
            <p v-if="errors.phone" class="err mono">{{ errors.phone }}</p>
          </div>
        </div>

        <div class="field">
          <label for="card-venue"><span class="mono">03</span>开卡门店</label>
          <select
            id="card-venue"
            v-model="form.venue"
            class="field-input"
            :class="{ error: errors.venue }"
            @change="errors.venue = ''"
          >
            <option value="" disabled>选择到店付款的门店</option>
            <option v-for="venue in venues" :key="venue.code" :value="venue.code">
              {{ venue.name }} · {{ venue.address }}
            </option>
          </select>
          <p v-if="errors.venue" class="err mono">{{ errors.venue }}</p>
        </div>

        <div class="field">
          <label for="card-note"><span class="mono">04</span>备注（选填）</label>
          <textarea
            id="card-note"
            v-model.trim="form.note"
            class="field-input field-note"
            maxlength="200"
            rows="2"
            placeholder="想练的课程、预计到店时间，可留空"
            @input="errors.note = ''"
          ></textarea>
          <p v-if="errors.note" class="err mono">{{ errors.note }}</p>
        </div>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <button type="submit" class="card-submit" :disabled="loading">
          <span>{{ loading ? '正在登记…' : '提交购卡登记' }}</span>
          <span class="mono">{{ selectedPlan.credits }} 次 / {{ selectedPlan.validDays }} 天</span>
          <b aria-hidden="true">→</b>
        </button>
        <p class="privacy mono">不在线收款、不自动续费；到店付款后由店员开卡。</p>
      </form>

      <div class="card-foot mono">
        <span>10 次 / 180 天</span>
        <span>三店通用</span>
        <span>可与朋友共享</span>
      </div>
    </template>

    <div v-else class="card-success" aria-live="polite">
      <p class="card-success-kicker mono">CARD REGISTERED / 购卡已登记</p>
      <h4>到店付款后开卡<em>。</em></h4>
      <dl class="card-detail mono">
        <div><dt>登记号</dt><dd>{{ result.orderNo }}</dd></div>
        <div><dt>姓名</dt><dd>{{ result.name }}</dd></div>
        <div><dt>手机号</dt><dd>{{ result.phone }}</dd></div>
        <div><dt>次卡</dt><dd>{{ result.planName }} · {{ result.credits }} 次</dd></div>
        <div v-if="result.venueName"><dt>门店</dt><dd>{{ result.venueName }}</dd></div>
        <div><dt>金额</dt><dd>¥{{ result.amount }} · 到店支付</dd></div>
      </dl>
      <p class="card-success-note">
        现在可微信搜索「VOLT 电压健身」进入小程序。店员开卡后，用同手机号登录即可看到次数并约课。
      </p>
      <div class="card-success-actions">
        <button type="button" class="card-submit" @click="goMini">
          去小程序
          <b aria-hidden="true">→</b>
        </button>
        <button type="button" class="card-again" @click="again">再登记一张</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { venues } from '../../../data/site.js'
import { ApiError, createCardOrder } from '../../../services/api/index.js'
import { isValidPhone, normalizePhone } from '../../../utils/phone.js'

const props = defineProps({
  active: { type: Boolean, default: false },
  selectedPlan: { type: Object, required: true },
})

const form = reactive({ name: '', phone: '', venue: '', note: '' })
const errors = reactive({ name: '', phone: '', venue: '', note: '' })
const loading = ref(false)
const error = ref('')
const result = ref(null)

watch(
  () => props.selectedPlan.code,
  () => {
    result.value = null
    error.value = ''
  }
)

function onPhoneInput(event) {
  const digits = normalizePhone(event.target.value)
  form.phone = digits
  event.target.value = digits
  errors.phone = ''
}

function validate() {
  errors.name = form.name ? '' : '请填写姓名'
  errors.phone = isValidPhone(form.phone) ? '' : '请填写正确的 11 位手机号'
  errors.venue = form.venue ? '' : '请选择开卡门店'
  errors.note = form.note.length > 200 ? '备注不能超过 200 字' : ''
  return !errors.name && !errors.phone && !errors.venue && !errors.note
}

function submit() {
  if (loading.value) return
  error.value = ''
  if (!validate()) return
  loading.value = true
  createCardOrder({
    name: form.name,
    phone: form.phone,
    planCode: props.selectedPlan.code,
    venueCode: form.venue,
    note: form.note,
  })
    .then((res) => {
      result.value = res?.order || null
      if (!result.value) throw new Error('missing order')
    })
    .catch((err) => {
      if (err instanceof ApiError && err.fields) {
        errors.name = err.fields.name || errors.name
        errors.phone = err.fields.phone || errors.phone
        errors.venue = err.fields.venue || errors.venue
        errors.note = err.fields.note || errors.note
      }
      error.value = err instanceof ApiError ? err.message : '购卡登记失败，请稍后再试。'
    })
    .finally(() => {
      loading.value = false
    })
}

function again() {
  result.value = null
  error.value = ''
  form.note = ''
}

function goMini() {
  window.location.hash = 'footer'
}
</script>

<style scoped>
.card-panel {
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
  overflow-y: auto;
  scrollbar-width: thin;
}
.card-panel.is-on { opacity: 1; transform: none; }

.card-head {
  flex: none;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid color-mix(in srgb, #0D0B08 14%, transparent);
}
.card-no {
  margin: 0;
  font-size: 9px;
  letter-spacing: .12em;
  color: #8A5200;
}
.card-head h3 {
  margin: 5px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(26px, 2.4vw, 34px);
  font-weight: 900;
  line-height: 1.03;
  letter-spacing: -.05em;
}
.card-head h3 em { font-style: normal; color: var(--acid); }
.card-chosen {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  text-align: right;
}
.card-chosen span {
  font-size: 7.5px;
  letter-spacing: .06em;
  color: color-mix(in srgb, #0D0B08 74%, transparent);
}
.card-chosen b {
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
  color: var(--ink);
}
.card-chosen strong {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 400;
  line-height: 1;
  color: #8A5200;
}

.card-intro {
  flex: none;
  margin: 14px 0 0;
  padding: 8px 10px;
  border-left: 3px solid var(--acid);
  background: color-mix(in srgb, #F59E0B 9%, transparent);
  font-family: var(--font-cn);
  font-size: 11px;
  line-height: 1.6;
  color: color-mix(in srgb, #0D0B08 76%, transparent);
}

.card-form {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 11px;
  padding-top: clamp(14px, 2.2vh, 20px);
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
.field-input.error {
  border-color: var(--acid);
  background: #FCEBDD;
}
.field-note { resize: vertical; min-height: 58px; line-height: 1.5; }
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
.err {
  margin: 0;
  font-size: 10px;
  line-height: 1.4;
  letter-spacing: .02em;
  color: #8A3B00;
}
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
.card-submit {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
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
.card-submit .mono {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 500;
  letter-spacing: .04em;
}
.card-submit b { font-size: 18px; line-height: 1; transition: transform .2s ease; }
.card-submit:hover { background: var(--ink); color: var(--acid); }
.card-submit:hover b { transform: translateX(3px); }
.card-submit:disabled { opacity: .65; cursor: wait; }
.privacy {
  margin: 0;
  font-size: 8px;
  line-height: 1.5;
  letter-spacing: .02em;
  color: color-mix(in srgb, #0D0B08 70%, transparent);
}
.card-foot {
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

.card-success {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  padding-top: 18px;
}
.card-success-kicker {
  margin: 0;
  font-size: 8px;
  letter-spacing: .1em;
  color: #8A5200;
}
.card-success h4 {
  margin: 8px 0 14px;
  font-family: var(--font-cn);
  font-size: clamp(27px, 2.5vw, 36px);
  font-weight: 900;
  line-height: 1.05;
  letter-spacing: -.04em;
}
.card-success h4 em { font-style: normal; color: var(--acid); }
.card-detail { margin: 0; display: flex; flex-direction: column; gap: 7px; }
.card-detail div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 6px;
  border-bottom: 1px dashed color-mix(in srgb, #0D0B08 18%, transparent);
  font-size: 10px;
}
.card-detail dt { color: color-mix(in srgb, #0D0B08 72%, transparent); }
.card-detail dd { margin: 0; font-weight: 500; text-align: right; }
.card-success-note {
  margin: 14px 0;
  padding: 8px 10px;
  border-left: 3px solid var(--acid);
  background: color-mix(in srgb, #F59E0B 9%, transparent);
  font-family: var(--font-cn);
  font-size: 11px;
  line-height: 1.6;
  color: color-mix(in srgb, #0D0B08 76%, transparent);
}
.card-success-actions { display: flex; flex-direction: column; gap: 8px; margin-top: auto; }
.card-again {
  min-height: 40px;
  border: 1px solid color-mix(in srgb, #0D0B08 30%, transparent);
  color: var(--ink);
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
}
.card-again:hover { background: color-mix(in srgb, #0D0B08 6%, transparent); }

@media (max-width: 900px) {
  .card-panel { flex: none; padding: 13px; }
  .card-head { flex-wrap: wrap; gap: 8px; padding-bottom: 9px; }
  .card-head h3 { font-size: 26px; }
  .card-chosen {
    flex-direction: row;
    align-items: baseline;
    gap: 7px;
    width: 100%;
    justify-content: flex-start;
  }
  .card-chosen strong { margin-left: auto; font-size: 19px; }
  .card-intro { margin-top: 10px; font-size: 10px; }
  .card-form { gap: 9px; padding-top: 10px; }
  .field-row { gap: 8px; }
  .field-input { min-height: 38px; font-size: 12px; }
  select.field-input { background-position: calc(100% - 12px) 16px, calc(100% - 7px) 16px; }
  .card-submit { min-height: 45px; font-size: 12px; }
  .card-submit .mono { display: none; }
  .privacy { font-size: 7px; }
  .card-foot { margin-top: 12px; font-size: 7px; }
}
</style>
