<template>
  <BaseModal :open="open" :busy="submitting" @close="close">
    <div class="cm-modal" role="dialog" aria-modal="true" :aria-label="dialogTitle">
          <template v-if="!done">
            <p class="cm-modal-status mono">{{ dialogStatus }}</p>
            <h3>{{ dialogTitle }}</h3>
            <p class="cm-modal-meta mono">{{ dialogMeta }}</p>

            <form class="cm-modal-form" novalidate @submit.prevent="submit">
              <div v-if="member" class="cm-member">
                <p class="mono cm-member-kicker">MEMBER / 会员身份</p>
                <p class="cm-member-name">
                  {{ member.nickname || 'VOLT 会员' }}
                  <span class="mono">{{ member.planName || '按次充电' }} · 余 {{ member.credits }} 次</span>
                </p>
              </div>

              <template v-else>
                <div v-if="kind === 'event'" class="cm-field">
                  <label for="cm-name">姓名</label>
                  <input
                    id="cm-name"
                    v-model.trim="form.name"
                    class="cm-input"
                    :class="{ error: fieldErrors.name }"
                    type="text"
                    maxlength="24"
                    autocomplete="name"
                    placeholder="怎么称呼你"
                    :aria-invalid="Boolean(fieldErrors.name)"
                    @input="fieldErrors.name = ''"
                  />
                  <p v-if="fieldErrors.name" class="cm-err mono">{{ fieldErrors.name }}</p>
                </div>

                <div class="cm-field">
                  <label for="cm-phone">手机号</label>
                  <input
                    id="cm-phone"
                    :value="form.phone"
                    class="cm-input"
                    :class="{ error: fieldErrors.phone }"
                    type="tel"
                    inputmode="numeric"
                    maxlength="11"
                    autocomplete="tel"
                    placeholder="11 位手机号"
                    :aria-invalid="Boolean(fieldErrors.phone)"
                    @input="onPhoneInput"
                  />
                  <p v-if="fieldErrors.phone" class="cm-err mono">{{ fieldErrors.phone }}</p>
                </div>
              </template>

              <p v-if="dialogError" class="cm-modal-error" role="alert">{{ dialogError }}</p>

              <button
                type="submit"
                class="btn btn-acid cm-modal-submit"
                :disabled="submitting || cooldown > 0"
              >
                {{
                  submitting
                    ? '正在提交…'
                    : cooldown > 0
                      ? `请等待 ${cooldown} 秒`
                      : kind === 'event'
                        ? '确认报名'
                        : '加入挑战'
                }}
              </button>
            </form>

            <p class="cm-modal-note">信息仅用于本次活动联系，不会用于推销。</p>
            <button type="button" class="cm-modal-close mono" @click="close">关闭</button>
          </template>

          <template v-else>
            <p class="cm-modal-status mono">CONFIRMED / 已确认</p>
            <h3>{{ done.title }}</h3>
            <p class="cm-modal-meta mono">{{ done.no }}</p>
            <p v-for="line in done.lines" :key="line" class="cm-modal-line">{{ line }}</p>
            <button type="button" class="btn btn-acid cm-modal-submit" @click="close">
              完成
            </button>
          </template>
    </div>
  </BaseModal>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import BaseModal from '../../ui/BaseModal.vue'
import { useCooldown } from '../../../composables/useCooldown.js'
import {
  ApiError,
  getWebSession,
  joinChallenge,
  joinEvent,
} from '../../../services/api/index.js'
import { isValidPhone, normalizePhone } from '../../../utils/phone.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  kind: { type: String, default: 'event' },
  event: { type: Object, default: null },
  challenge: { type: Object, required: true },
})
const emit = defineEmits(['close', 'joined', 'refresh'])

const done = ref(null)
const submitting = ref(false)
const { remaining: cooldown, start: startCooldown } = useCooldown()
const dialogError = ref('')
const member = ref(null)
const form = reactive({ name: '', phone: '' })
const fieldErrors = reactive({ name: '', phone: '' })

const dialogTitle = computed(() =>
  props.kind === 'event' ? props.event?.title || '活动报名' : props.challenge.title
)
const dialogStatus = computed(() =>
  props.kind === 'event' ? 'EVENT SIGN-UP / 活动报名' : 'WEEKLY CHALLENGE / 加入挑战'
)
const dialogMeta = computed(() => {
  if (props.kind === 'event') {
    const event = props.event
    return event ? `${event.time} · ${event.venue} · 余 ${event.remaining} 位` : ''
  }
  return `本周练满 ${props.challenge.sessions} 次 · 剩 ${props.challenge.days} 天`
})

function resetForm() {
  form.name = ''
  form.phone = ''
  fieldErrors.name = ''
  fieldErrors.phone = ''
  dialogError.value = ''
  done.value = null
}

function onPhoneInput(event) {
  const digits = normalizePhone(event.target.value)
  form.phone = digits
  event.target.value = digits
  fieldErrors.phone = ''
}

async function submit() {
  if (submitting.value || done.value || cooldown.value > 0) return
  dialogError.value = ''
  const asMember = Boolean(member.value)
  const needsName = !asMember && props.kind === 'event'
  fieldErrors.name = needsName && !form.name.trim() ? '请填写姓名' : ''
  fieldErrors.phone =
    !asMember && !isValidPhone(form.phone) ? '请填写正确的 11 位手机号' : ''
  if (fieldErrors.name || fieldErrors.phone) return

  submitting.value = true
  const payload = asMember ? {} : { name: form.name.trim(), phone: form.phone }
  try {
    if (props.kind === 'event') {
      const result = await joinEvent(props.event.id, payload)
      emit('joined', { kind: 'event', event: result.event })
      done.value = {
        title: '报名成功。',
        no: `SIGN-UP / ${result.signupNo}`,
        lines: [
          `${result.event.title} · ${result.event.time}`,
          `${result.event.venue}，开课前 10 分钟到场。`,
        ],
      }
    } else {
      const result = await joinChallenge(payload)
      emit('joined', { kind: 'challenge', memberNo: result.memberNo })
      done.value = {
        title: '已加入本周挑战。',
        no: `MEMBER / ${result.memberNo}`,
        lines: [
          `本周练满 ${props.challenge.sessions} 次，解锁「电流不断」贴纸。`,
          '训练记录以门店核销为准。',
        ],
      }
    }
  } catch (error) {
    if (error instanceof ApiError && error.fields) {
      if (error.fields.name) fieldErrors.name = error.fields.name
      if (error.fields.phone) fieldErrors.phone = error.fields.phone
    }
    dialogError.value = error instanceof ApiError ? error.message : '提交失败，请稍后再试。'
    if (error instanceof ApiError && [401, 403].includes(error.status)) {
      member.value = null
      dialogError.value = '登录已过期，请填写报名信息或重新登录。'
      nextTick(() => {
        const id = props.kind === 'event' ? 'cm-name' : 'cm-phone'
        document.getElementById(id)?.focus()
      })
    }
    if (error instanceof ApiError && ['EVENT_FULL', 'DUPLICATE_SIGNUP'].includes(error.code)) {
      emit('refresh')
    }
    if (error instanceof ApiError && error.code === 'RATE_LIMITED') {
      startCooldown(5, () => {
        dialogError.value = ''
      })
    }
  } finally {
    submitting.value = false
  }
}

function close() {
  if (submitting.value) return
  emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    member.value = getWebSession()?.member || null
    resetForm()
  }
)
</script>

<style scoped>
.cm-modal {
  width: min(430px, 100%);
  max-height: min(86vh, 640px);
  overflow-y: auto;
  padding: 24px;
  border: 1px solid color-mix(in srgb, #F59E0B 54%, transparent);
  background: var(--paper);
  color: var(--ink);
  box-shadow: 10px 10px 0 color-mix(in srgb, #F59E0B 46%, #0D0B08);
}
.cm-modal-status {
  display: inline-block;
  margin: 0;
  padding: 5px 9px;
  border: 1px solid color-mix(in srgb, #B45309 42%, transparent);
  background: color-mix(in srgb, #F59E0B 14%, transparent);
  font-size: 11px;
  letter-spacing: .08em;
  color: #7A3E00;
}
.cm-modal h3 {
  margin: 13px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(24px, 3.4vw, 30px);
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: -.04em;
}
.cm-modal-meta {
  margin: 8px 0 0;
  font-size: 9px;
  letter-spacing: .04em;
  color: color-mix(in srgb, #0D0B08 58%, transparent);
}
.cm-modal-form { display: flex; flex-direction: column; gap: 12px; margin-top: 18px; }
.cm-member {
  padding: 10px 11px;
  border: 1px solid color-mix(in srgb, #F59E0B 36%, transparent);
  background: color-mix(in srgb, #F59E0B 8%, transparent);
}
.cm-member-kicker {
  margin: 0;
  font-size: 8px;
  letter-spacing: .08em;
  color: #8A5200;
}
.cm-member-name {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin: 5px 0 0;
  font-family: var(--font-cn);
  font-size: 14px;
  font-weight: 900;
}
.cm-member-name span {
  overflow: hidden;
  font-size: 8px;
  font-weight: 400;
  color: color-mix(in srgb, #0D0B08 60%, transparent);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cm-field { display: flex; flex-direction: column; gap: 5px; }
.cm-field label { font-family: var(--font-cn); font-size: 11px; font-weight: 700; }
.cm-input {
  width: 100%;
  min-height: 42px;
  padding: 8px 10px;
  border: 1px solid color-mix(in srgb, #0D0B08 28%, transparent);
  border-radius: 0;
  background: #fff;
  color: var(--ink);
  font-family: var(--font-cn);
  font-size: 13px;
}
.cm-input::placeholder { color: color-mix(in srgb, #0D0B08 38%, transparent); }
.cm-input:focus { outline: none; border-color: var(--acid); background: #FFFDF6; }
.cm-input.error { border-color: var(--acid); background: #FCEBDD; }
.cm-err { margin: 0; font-size: 8px; letter-spacing: .02em; color: #8A3B00; }
.cm-modal-error {
  margin: 0;
  padding: 7px 9px;
  border-left: 3px solid var(--acid);
  background: color-mix(in srgb, #F59E0B 12%, transparent);
  font-family: var(--font-cn);
  font-size: 10px;
  line-height: 1.45;
  color: #7A3B00;
}
.cm-modal-submit {
  width: 100%;
  min-height: 44px;
  margin-top: 2px;
  justify-content: center;
  font-family: var(--font-cn);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: .04em;
  text-transform: none;
}
.cm-modal-submit:disabled { opacity: .65; cursor: wait; transform: none; }
.cm-modal-note { margin: 12px 0 0; font-family: var(--font-cn); font-size: 9px; line-height: 1.55; color: color-mix(in srgb, #0D0B08 68%, transparent); }
.cm-modal-close {
  margin-top: 12px;
  padding: 0 0 2px;
  border-bottom: 1px solid color-mix(in srgb, #0D0B08 30%, transparent);
  font-size: 8px;
  letter-spacing: .1em;
  color: color-mix(in srgb, #0D0B08 58%, transparent);
}
.cm-modal-line {
  margin: 10px 0 0;
  font-family: var(--font-cn);
  font-size: 12px;
  line-height: 1.7;
  color: color-mix(in srgb, #0D0B08 72%, transparent);
}
@media (max-width: 900px) {
  .cm-modal { padding: 19px; box-shadow: 6px 6px 0 color-mix(in srgb, #F59E0B 46%, #0D0B08); }
  .cm-modal h3 { font-size: 24px; }
}
</style>
