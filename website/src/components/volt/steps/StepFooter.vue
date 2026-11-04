<template>
  <div class="screen-inner closing-dark" :class="{ 'is-on': active }" data-scrollable>
    <header class="closing-head mono">
      <span><i aria-hidden="true"></i>05 / END CREDITS</span>
      <span>VOLT · 2026 · GUANGZHOU</span>
    </header>

    <div class="closing-body">
      <section class="closing-copy">
        <p class="closing-kicker mono">按次付费 · 不办年卡</p>
        <h2>下一次<br />电流见<em>。</em></h2>
        <p class="closing-sub">想来的时候，再接入就好。</p>

        <div class="closing-actions">
          <button type="button" class="closing-btn" @click="$emit('go', 3, { plan: 'PLAN-01' })">
            ¥{{ brand.trialPrice }} 首次接入 <b aria-hidden="true">→</b>
          </button>
          <button type="button" class="closing-ghost" @click="$emit('go', 0)">
            回到开场
          </button>
        </div>
      </section>

      <aside class="member-pass" aria-label="VOLT 社员微信会员小程序">
        <div class="pass-top">
          <span class="pass-kicker mono">VOLT 会员 / 微信小程序</span>
          <span class="pass-free mono">免费注册</span>
        </div>

        <div class="pass-main">
          <div class="pass-copy">
            <p class="pass-eyebrow mono">MEMBER MINI-PROGRAM</p>
            <h3>课在手机里。</h3>
            <p class="pass-line">次卡到店开卡后，约课 · 查余位 · 进场</p>
            <button
              type="button"
              class="pass-search mono"
              aria-live="polite"
              @click="copyMiniProgram"
            >
              {{ copyLabel }}<b aria-hidden="true">{{ copied ? '✓' : '→' }}</b>
            </button>
          </div>

          <div class="pass-phone" aria-hidden="true">
            <img
              class="pp-img"
              src="/images/mini-screen-home.png"
              alt=""
              loading="lazy"
              decoding="async"
            />
          </div>
        </div>

        <div class="pass-foot mono">
          <span>VOLT MEMBER / ACCOUNT</span>
          <button type="button" class="pass-community" @click="$emit('community')">
            进入社区 →
          </button>
        </div>
      </aside>
    </div>

    <div class="closing-info">
      <div class="contact">
        <p class="label mono">CONTACT / 联系我们</p>
        <a class="phone-link" :href="`tel:${brand.phone.replace(/-/g, '')}`">
          {{ brand.phone }}
        </a>
      </div>

      <div class="stations">
        <p class="label mono">STATIONS / 三处充电站</p>
        <div class="station-grid">
          <button
            v-for="v in venues"
            :key="v.code"
            type="button"
            class="station-row"
            :aria-label="`选 ${v.name} 去预约`"
            @click="$emit('go', 3, { venue: v.code })"
          >
            <span class="station-code mono">{{ v.code }}</span>
            <b>{{ v.name }}</b>
            <span class="station-meta mono">{{ v.address }} · {{ v.hours }}</span>
          </button>
        </div>
      </div>
    </div>

    <footer class="closing-foot mono">
      <span>© 2026 VOLT VOLTAGE FITNESS</span>
      <span>PLUG IN. SWEAT. REPEAT.</span>
      <span>GUANGZHOU · 23.13°N</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { brand, venues } from '../../../data/site.js'

defineProps({
  active: { type: Boolean, default: false },
})
defineEmits(['go', 'community'])

const copied = ref(false)
let copyTimer = 0

const copyLabel = computed(() => {
  if (copied.value) return '已复制，去微信粘贴搜索'
  return '微信搜「VOLT 电压健身」'
})

async function copyMiniProgram() {
  try {
    await navigator.clipboard.writeText('VOLT 电压健身')
    copied.value = true
  } catch {
    copied.value = false
  }
  window.clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => {
    copied.value = false
  }, 1800)
}

onBeforeUnmount(() => window.clearTimeout(copyTimer))
</script>

<style scoped>
.closing-dark {
  color: var(--paper);
  gap: 0;
  overflow-y: auto;
  scrollbar-width: none;
}
.closing-dark::-webkit-scrollbar { display: none; }

.closing-head {
  flex: none;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid color-mix(in srgb, oldlace 16%, transparent);
  font-size: 9px;
  letter-spacing: .12em;
  color: color-mix(in srgb, oldlace 64%, transparent);
}
.closing-head span { display: inline-flex; align-items: center; gap: 8px; }
.closing-head i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--acid);
}

.closing-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: clamp(28px, 4vw, 62px);
  align-items: center;
  padding: clamp(18px, 2.6vh, 30px) 0;
}

.closing-copy { min-width: 0; }
.closing-kicker {
  margin: 0;
  font-size: 9px;
  letter-spacing: .12em;
  color: color-mix(in srgb, orange 82%, transparent);
}
.closing-copy h2 {
  margin: 12px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(56px, 5.5vw, 84px);
  font-weight: 900;
  line-height: .94;
  letter-spacing: -.06em;
}
.closing-copy h2 em { font-style: normal; color: var(--acid); }
.closing-sub {
  max-width: 400px;
  margin: 14px 0 0;
  font-family: var(--font-cn);
  font-size: 15px;
  line-height: 1.75;
  color: color-mix(in srgb, oldlace 66%, transparent);
}
.closing-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}
.closing-btn {
  display: inline-flex;
  align-items: center;
  gap: 22px;
  min-height: 46px;
  padding: 10px 16px;
  background: var(--acid);
  color: var(--ink);
  font-family: var(--font-cn);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: .03em;
  transition: background .2s ease, color .2s ease;
}
.closing-btn b { font-size: 17px; line-height: 1; }
.closing-btn:hover { background: var(--ink); color: var(--acid); }
.closing-ghost {
  min-height: 46px;
  padding: 10px 15px;
  border: 1px solid color-mix(in srgb, oldlace 26%, transparent);
  color: var(--paper);
  font-family: var(--font-cn);
  font-size: 12px;
  font-weight: 700;
  transition: background .2s ease, color .2s ease, border-color .2s ease;
}
.closing-ghost:hover {
  background: var(--paper);
  border-color: var(--paper);
  color: var(--ink);
}

.member-pass {
  align-self: center;
  display: flex;
  flex-direction: column;
  min-height: clamp(330px, 48vh, 460px);
  background: var(--acid);
  color: var(--ink);
  opacity: 0;
  transform: translateY(16px);
  transition: opacity .5s ease .12s, transform .7s var(--ease-gate) .12s;
}
.closing-dark.is-on .member-pass { opacity: 1; transform: none; }

.pass-top {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 18px;
  border-bottom: 1px solid color-mix(in srgb, black 24%, transparent);
}
.pass-kicker { font-size: 8px; letter-spacing: .08em; }
.pass-free { font-size: 8px; letter-spacing: .06em; }

.pass-main {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 154px;
  gap: clamp(18px, 2.4vw, 34px);
  align-items: center;
  padding: clamp(18px, 2.4vh, 26px) 20px;
}
.pass-copy {
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.pass-eyebrow {
  margin: 0;
  font-size: 8px;
  letter-spacing: .12em;
  color: color-mix(in srgb, black 62%, transparent);
}
.pass-copy h3 {
  margin: 8px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(30px, 3vw, 46px);
  font-weight: 900;
  line-height: 1.02;
  letter-spacing: -.055em;
}
.pass-line {
  margin: 9px 0 0;
  font-family: var(--font-cn);
  font-size: 13px;
  font-weight: 700;
  color: color-mix(in srgb, black 74%, transparent);
}
.pass-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: 100%;
  margin-top: clamp(16px, 3vh, 30px);
  padding-top: 12px;
  border-top: 1px dashed color-mix(in srgb, black 30%, transparent);
  color: var(--ink);
  text-align: left;
  font-size: 10px;
  letter-spacing: .02em;
}
.pass-search:hover b { transform: translateX(3px); }
.pass-search b {
  font-size: 17px;
  line-height: 1;
  transition: transform .18s ease;
}

.pass-phone {
  justify-self: end;
  width: 154px;
  height: 264px;
  padding: 6px;
  background: var(--ink);
}
.pp-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
}

.pass-foot {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 18px;
  border-top: 1px solid color-mix(in srgb, black 24%, transparent);
  font-size: 8px;
  letter-spacing: .1em;
  color: color-mix(in srgb, black 72%, transparent);
}
.pass-community {
  padding: 0 0 1px;
  border-bottom: 1px solid color-mix(in srgb, black 42%, transparent);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: .06em;
  transition: border-color .18s ease;
}
.pass-community:hover { border-color: var(--ink); }

.closing-info {
  flex: none;
  display: grid;
  grid-template-columns: minmax(0, .72fr) minmax(0, 1.28fr);
  gap: clamp(24px, 4vw, 62px);
  padding: 13px 0 11px;
  border-top: 1px solid color-mix(in srgb, oldlace 16%, transparent);
}
.label {
  margin: 0 0 7px;
  font-size: 8px;
  letter-spacing: .12em;
  color: color-mix(in srgb, oldlace 62%, transparent);
}
.phone-link {
  display: inline-block;
  font-family: var(--font-display);
  font-size: clamp(25px, 2.3vw, 33px);
  line-height: 1;
  letter-spacing: .02em;
  color: var(--paper);
  transition: color .2s ease;
}
.phone-link:hover { color: var(--acid); }

.station-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.station-row {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 2px 6px;
  padding-top: 1px;
  color: inherit;
  text-align: left;
}
.station-row:hover b { text-decoration: underline; text-underline-offset: 2px; }
.station-row:hover .station-code { color: var(--paper); }
.station-code {
  font-size: 7px;
  letter-spacing: .06em;
  color: color-mix(in srgb, orange 76%, transparent);
}
.station-row b {
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 900;
  line-height: 1.3;
}
.station-meta {
  grid-column: 2;
  font-size: 7px;
  line-height: 1.45;
  color: color-mix(in srgb, oldlace 58%, transparent);
}

.closing-foot {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 9px;
  border-top: 1px solid color-mix(in srgb, oldlace 12%, transparent);
  font-size: 7.5px;
  letter-spacing: .1em;
  color: color-mix(in srgb, oldlace 56%, transparent);
}

@media (max-height: 800px) and (min-width: 901px) {
  .closing-copy h2 { font-size: clamp(48px, 4.8vw, 70px); }
  .closing-sub { margin-top: 10px; }
  .closing-actions { margin-top: 14px; }
  .member-pass { min-height: clamp(300px, 44vh, 390px); }
  .pass-copy h3 { font-size: clamp(28px, 2.6vw, 38px); }
  .pass-phone { width: 138px; height: 238px; }
  .closing-info { padding: 10px 0 8px; }
}

@media (max-width: 900px) {
  .closing-body {
    flex: none;
    grid-template-columns: 1fr;
    gap: 16px;
    align-items: start;
    padding: 14px 0 2px;
  }
  .closing-copy h2 { font-size: clamp(44px, 12.5vw, 58px); }
  .closing-sub { margin-top: 8px; font-size: 11px; }
  .closing-actions { margin-top: 12px; }
  .closing-btn, .closing-ghost { min-height: 42px; padding: 9px 12px; font-size: 12px; }
  .closing-btn { flex: 1; justify-content: space-between; gap: 10px; }

  .member-pass { min-height: 0; width: 100%; }
  .pass-top { padding: 9px 12px; }
  .pass-kicker { font-size: 7px; }
  .pass-main {
    grid-template-columns: minmax(0, 1fr) 118px;
    gap: 12px;
    padding: 14px 12px;
  }
  .pass-copy h3 { font-size: 27px; }
  .pass-line { font-size: 11px; margin-top: 6px; }
  .pass-search { margin-top: 14px; padding-top: 9px; font-size: 8px; }
  .pass-phone { width: 118px; height: 204px; padding: 5px; }
  .pass-foot { padding: 8px 12px; font-size: 7px; }

  .closing-info {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 11px 0 9px;
  }
  .phone-link { font-size: 26px; }
  .station-grid { grid-template-columns: 1fr; gap: 6px; }
  .station-row {
    grid-template-columns: 28px minmax(0, 1fr) auto;
    align-items: baseline;
    padding-bottom: 5px;
    border-bottom: 1px solid color-mix(in srgb, oldlace 10%, transparent);
  }
  .station-meta {
    grid-column: auto;
    font-size: 6.5px;
    text-align: right;
  }
  .station-row b { font-size: 10px; }
  .closing-foot {
    flex-wrap: wrap;
    gap: 3px 10px;
    padding-top: 8px;
    font-size: 7px;
  }
  .closing-foot span:nth-child(2) { display: none; }
}
</style>
