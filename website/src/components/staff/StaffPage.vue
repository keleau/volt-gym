<template>
  <div class="staff-page">
    <header class="staff-head">
      <a class="staff-brand" href="#top">VOLT<span>//</span></a>
      <span class="mono">STAFF / 店员开卡</span>
      <a class="staff-back mono" href="#top">返回官网 →</a>
    </header>

    <main class="staff-shell">
      <section class="staff-intro">
        <p class="mono">CARD DESK / 购卡开卡</p>
        <h1>确认到店付款，<em>开卡。</em></h1>
        <p>
          输入店员密钥后按手机号查待开卡登记。确认后会员刷新小程序或官网，次数自动到账。
        </p>
      </section>

      <section class="staff-query">
        <div class="field">
          <label for="staff-key">店员密钥</label>
          <input
            id="staff-key"
            v-model="key"
            type="password"
            autocomplete="off"
            placeholder="VOLT_STAFF_KEY"
            @keyup.enter="load"
          />
        </div>
        <div class="field">
          <label for="staff-phone">手机号（留空查全部待开卡）</label>
          <input
            id="staff-phone"
            v-model.trim="phone"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            placeholder="例如 13800138000"
            @keyup.enter="load"
          />
        </div>
        <button type="button" class="staff-search" :disabled="loading" @click="load">
          {{ loading ? '查询中…' : '查询待开卡' }}
        </button>
      </section>

      <p v-if="notice" class="staff-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="staff-error" role="alert">{{ error }}</p>

      <section v-if="orders.length" class="staff-list" aria-live="polite">
        <header class="staff-list-head mono">
          <span>PENDING / 待开卡</span>
          <span>{{ orders.length }} 条</span>
        </header>

        <article v-for="order in orders" :key="order.orderNo" class="staff-order">
          <div class="staff-order-main">
            <div class="staff-order-title">
              <span class="mono">{{ order.orderNo }}</span>
              <b>{{ order.name }} · {{ order.phone }}</b>
            </div>
            <dl>
              <div><dt>次卡</dt><dd>{{ order.planName }} · {{ order.credits }} 次</dd></div>
              <div><dt>金额</dt><dd>¥{{ order.amount }}</dd></div>
              <div v-if="order.venueName"><dt>门店</dt><dd>{{ order.venueName }}</dd></div>
              <div><dt>登记时间</dt><dd>{{ formatDate(order.createdAt) }}</dd></div>
              <div v-if="order.note"><dt>备注</dt><dd>{{ order.note }}</dd></div>
            </dl>
          </div>
          <button
            type="button"
            class="staff-activate"
            :disabled="activating === order.orderNo"
            @click="activate(order)"
          >
            {{ activating === order.orderNo ? '开卡中…' : '确认开卡' }}
          </button>
        </article>
      </section>

      <p v-else-if="searched && !loading" class="staff-empty mono">没有待开卡登记。</p>

      <p class="staff-note mono">
        开卡只代表店员确认收款；权益会在会员下次登录或刷新时合并到手机号账号。
      </p>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  ApiError,
  activateStaffCardOrder,
  getStaffCardOrders,
} from '../../services/api/index.js'

const STAFF_KEY = 'volt.staff.key'
const key = ref('')
const phone = ref('')
const orders = ref([])
const loading = ref(false)
const activating = ref('')
const error = ref('')
const notice = ref('')
const searched = ref(false)

onMounted(() => {
  try {
    key.value = sessionStorage.getItem(STAFF_KEY) || ''
  } catch {
    key.value = ''
  }
  if (key.value) load()
})

function rememberKey() {
  try {
    sessionStorage.setItem(STAFF_KEY, key.value)
  } catch {
    /* storage blocked; key still works for this page */
  }
}

function load(preserveNotice = false) {
  if (!key.value.trim()) {
    error.value = '请输入店员密钥。'
    orders.value = []
    return
  }
  loading.value = true
  error.value = ''
  if (!preserveNotice) notice.value = ''
  getStaffCardOrders({ key: key.value.trim(), phone: phone.value.trim(), status: 'pending' })
    .then((res) => {
      orders.value = res?.orders || []
      searched.value = true
      rememberKey()
    })
    .catch((err) => {
      orders.value = []
      searched.value = true
      error.value = err instanceof ApiError ? err.message : '查询失败，请检查后端是否启动。'
    })
    .finally(() => {
      loading.value = false
    })
}

function activate(order) {
  if (activating.value) return
  const ok = window.confirm(`确认 ${order.name} 的「${order.planName}」已到店付款并开卡？`)
  if (!ok) return
  activating.value = order.orderNo
  error.value = ''
  notice.value = ''
  activateStaffCardOrder({ key: key.value.trim(), orderNo: order.orderNo })
    .then((res) => {
      const applied = res?.order?.applied
      notice.value = applied
        ? `${order.orderNo} 已开卡，${order.credits} 次已到账。`
        : `${order.orderNo} 已开卡；会员下次登录或刷新后到账。`
      load(true)
    })
    .catch((err) => {
      error.value = err instanceof ApiError ? err.message : '开卡失败，请稍后再试。'
    })
    .finally(() => {
      activating.value = ''
    })
}

function formatDate(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.staff-page {
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  background: var(--ink);
  color: var(--paper);
}
.staff-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  max-width: 1040px;
  margin: 0 auto;
  padding: 18px var(--pad);
  border-bottom: 1px solid color-mix(in srgb, oldlace 16%, transparent);
  font-size: 9px;
  letter-spacing: .1em;
  color: color-mix(in srgb, oldlace 62%, transparent);
}
.staff-brand {
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: .04em;
  color: var(--paper);
}
.staff-brand span {
  display: inline-block;
  margin-left: 1px;
  padding: 0 4px;
  background: var(--acid);
  color: var(--ink);
}
.staff-back:hover { color: var(--acid); }

.staff-shell {
  max-width: 1040px;
  margin: 0 auto;
  padding: clamp(28px, 5vh, 56px) var(--pad) 80px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.staff-intro p:first-child {
  margin: 0;
  font-size: 9px;
  letter-spacing: .12em;
  color: color-mix(in srgb, orange 82%, transparent);
}
.staff-intro h1 {
  margin: 8px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(38px, 6vw, 64px);
  font-weight: 900;
  line-height: 1.02;
  letter-spacing: -.055em;
}
.staff-intro h1 em { font-style: normal; color: var(--acid); }
.staff-intro p:last-child {
  max-width: 620px;
  margin: 12px 0 0;
  font-family: var(--font-cn);
  font-size: 13px;
  line-height: 1.7;
  color: color-mix(in srgb, oldlace 64%, transparent);
}

.staff-query {
  display: grid;
  grid-template-columns: minmax(200px, .8fr) minmax(220px, 1fr) auto;
  gap: 10px;
  align-items: end;
  padding: 16px;
  border: 1px solid color-mix(in srgb, oldlace 18%, transparent);
  background: var(--panel);
}
.field { display: flex; flex-direction: column; gap: 6px; }
.field label {
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
  color: color-mix(in srgb, oldlace 72%, transparent);
}
.field input {
  width: 100%;
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid color-mix(in srgb, oldlace 24%, transparent);
  border-radius: 0;
  background: #100D09;
  color: var(--paper);
  font-family: var(--font-mono);
  font-size: 12px;
}
.field input:focus {
  outline: none;
  border-color: var(--acid);
}
.staff-search {
  min-height: 40px;
  padding: 8px 16px;
  background: var(--acid);
  color: var(--ink);
  font-family: var(--font-cn);
  font-size: 12px;
  font-weight: 900;
}
.staff-search:disabled { opacity: .6; cursor: wait; }

.staff-notice,
.staff-error {
  margin: 0;
  padding: 9px 11px;
  font-family: var(--font-cn);
  font-size: 12px;
  line-height: 1.55;
}
.staff-notice {
  border-left: 3px solid var(--acid);
  background: color-mix(in srgb, #F59E0B 12%, transparent);
  color: color-mix(in srgb, oldlace 86%, transparent);
}
.staff-error {
  border-left: 3px solid #C2410C;
  background: color-mix(in srgb, #C2410C 14%, transparent);
  color: #FFD9C2;
}

.staff-list { display: flex; flex-direction: column; gap: 8px; }
.staff-list-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0 6px;
  border-bottom: 1px solid color-mix(in srgb, oldlace 14%, transparent);
  font-size: 8px;
  letter-spacing: .1em;
  color: color-mix(in srgb, oldlace 56%, transparent);
}
.staff-order {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, oldlace 16%, transparent);
  background: color-mix(in srgb, var(--panel) 82%, transparent);
}
.staff-order-main { min-width: 0; flex: 1; }
.staff-order-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}
.staff-order-title span {
  font-size: 8px;
  letter-spacing: .06em;
  color: var(--acid);
}
.staff-order-title b {
  font-family: var(--font-cn);
  font-size: 15px;
  font-weight: 900;
}
.staff-order dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
  margin: 10px 0 0;
}
.staff-order dl div { display: flex; gap: 8px; font-size: 10px; }
.staff-order dt {
  flex: none;
  color: color-mix(in srgb, oldlace 48%, transparent);
}
.staff-order dd {
  margin: 0;
  color: color-mix(in srgb, oldlace 82%, transparent);
  overflow-wrap: anywhere;
}
.staff-activate {
  flex: none;
  align-self: center;
  min-width: 104px;
  min-height: 40px;
  padding: 8px 14px;
  border: 1px solid var(--acid);
  color: var(--acid);
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 900;
  transition: background .18s ease, color .18s ease;
}
.staff-activate:hover { background: var(--acid); color: var(--ink); }
.staff-activate:disabled { opacity: .6; cursor: wait; }
.staff-empty {
  margin: 18px 0;
  font-size: 11px;
  letter-spacing: .06em;
  color: color-mix(in srgb, oldlace 52%, transparent);
}
.staff-note {
  margin: 8px 0 0;
  font-size: 8px;
  line-height: 1.6;
  color: color-mix(in srgb, oldlace 42%, transparent);
}

@media (max-width: 760px) {
  .staff-head { flex-wrap: wrap; gap: 8px; }
  .staff-query { grid-template-columns: 1fr; align-items: stretch; }
  .staff-order { flex-direction: column; gap: 12px; }
  .staff-activate { align-self: stretch; }
  .staff-order dl { grid-template-columns: 1fr; }
}
</style>
