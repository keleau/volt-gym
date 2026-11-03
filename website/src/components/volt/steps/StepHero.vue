<template>
  <div class="hero" :class="{ 'is-on': active, 'is-intro': intro }">
    <video
      ref="videoEl"
      class="hero-video"
      :poster="poster"
      src="/media/pulse-film.mp4"
      muted
      loop
      playsinline
      preload="auto"
      disablepictureinpicture
      @timeupdate="onTime"
      @loadedmetadata="onMeta"
      @play="playing = true"
      @pause="playing = false"
    ></video>

    <div class="hero-wash" aria-hidden="true"></div>
    <div class="hero-grain" aria-hidden="true"></div>
    <span class="hero-frame mono" aria-hidden="true">FRAME 01 / 05 · 1280 × 720</span>

    <div class="hero-inner">
      <div class="hero-copy">
        <p class="eyebrow">
          <span class="live-dot" aria-hidden="true"></span>
          <span>VOLT FILM / LIVE TRAINING · 广州三店</span>
        </p>

        <h1 class="hero-title" aria-label="接入训练电流">
          <span>接入</span>
          <span>训练<mark>电流</mark></span>
        </h1>

        <p class="hero-slogan">PLUG IN. SWEAT. REPEAT.</p>
        <p class="hero-sub">按次付费，不办年卡。专业教练带练，没有推销，只按这一次付钱。</p>

        <div class="hero-actions">
          <button type="button" class="btn btn-acid hero-cta" @click="$emit('go', 3, { plan: 'PLAN-01' })">
            <span class="hero-cta-price"><small>¥</small><b>{{ brand.trialPrice }}</b></span>
            <span class="hero-cta-label">立即接入 · 按次付费</span>
          </button>
          <button type="button" class="btn hero-ghost" @click="$emit('go', 2)">
            查看五门课程
          </button>
        </div>
      </div>

      <aside class="hero-ledger" aria-label="VOLT 会员微信小程序">
        <div class="ledger-head mono">
          <span>VOLT MEMBER</span>
          <i aria-hidden="true"></i>
          <span>FREE TO JOIN</span>
        </div>

        <div class="member-mini">
          <p class="mm-title">课在手机里。</p>
          <p class="mm-line">约课 · 查余位 · 进场码，一个小程序全搞定</p>

          <div class="mm-qr">
            <img
              class="mm-qr-img"
              src="/images/mini-qr.png"
              alt="VOLT 电压健身会员小程序二维码"
              width="88"
              height="88"
            />
            <div class="mm-qr-copy">
              <p class="mm-qr-title">微信扫一扫，免费入会</p>
              <p class="mm-qr-sub">新人首节 ¥{{ brand.trialPrice }} · 预约到店扫码核销</p>
            </div>
          </div>

          <p class="mm-search mono">微信搜「VOLT 电压健身」</p>
        </div>

        <p class="ledger-cue mono">
          <span>SCROLL TO CROSS THE GATE</span>
          <b aria-hidden="true">↓</b>
        </p>
      </aside>
    </div>

    <div class="hero-player">
      <button
        type="button"
        class="player-toggle mono"
        :aria-label="playing ? '暂停视频' : '播放视频'"
        @click="toggle"
      >
        <span class="toggle-icon" aria-hidden="true">{{ playing ? 'II' : '▶' }}</span>
        {{ playing ? 'PAUSE' : 'PLAY' }}
      </button>

      <span class="player-time mono">{{ currentLabel }}</span>

      <button
        type="button"
        class="player-track"
        :aria-label="`视频进度 ${Math.round(progress)}%`"
        @click="seek"
      >
        <i :style="{ width: `${progress}%` }"></i>
        <b :style="{ left: `${progress}%` }"></b>
      </button>

      <span class="player-time mono">{{ durationLabel }}</span>
      <span class="player-tag mono">VOLT FILM · 训练现场</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { brand } from '../../../data/site.js'

const props = defineProps({
  active: { type: Boolean, default: false },
  intro: { type: Boolean, default: false },
})
defineEmits(['go'])

const poster = '/images/film-poster.jpg'
const videoEl = ref(null)
const playing = ref(false)
const current = ref(0)
const duration = ref(13)

const progress = computed(() =>
  duration.value ? Math.min(100, (current.value / duration.value) * 100) : 0
)

function formatTime(value) {
  const total = Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}
const currentLabel = computed(() => formatTime(current.value))
const durationLabel = computed(() => formatTime(duration.value))

function play() {
  const video = videoEl.value
  if (!video) return
  video.play().catch(() => {
    playing.value = false
  })
}

function toggle() {
  const video = videoEl.value
  if (!video) return
  if (video.paused) play()
  else video.pause()
}

function onTime() {
  const video = videoEl.value
  if (!video) return
  current.value = video.currentTime
}

function onMeta() {
  const video = videoEl.value
  if (video && Number.isFinite(video.duration)) duration.value = video.duration
}

function seek(event) {
  const video = videoEl.value
  if (!video) return
  const rect = event.currentTarget.getBoundingClientRect()
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  video.currentTime = ratio * (video.duration || duration.value)
  current.value = video.currentTime
}

watch(
  () => props.active,
  (active) => {
    const video = videoEl.value
    if (!video) return
    // 入场期间就预启动影片（幕布盖住、opacity 0），幕布展开时画面已是运动中，
    // 不会先露出 poster / 首帧静止图。
    if (active) play()
    else video.pause()
  },
  { immediate: true }
)

onMounted(() => {
  if (props.active) play()
})

onBeforeUnmount(() => {
  videoEl.value?.pause()
})
</script>

<style scoped>
.hero {
  position: relative;
  height: 100%;
  overflow: hidden;
  background: var(--ink);
  color: var(--paper);
}

.hero-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 42%;
  filter: saturate(.82) contrast(1.04);
  transition: opacity .8s ease;
}
.hero.is-intro .hero-video { opacity: 0; }

.hero-wash {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(0, 0, 0, .88) 0%, rgba(0, 0, 0, .52) 44%, rgba(0, 0, 0, .12) 78%),
    linear-gradient(0deg, rgba(0, 0, 0, .92) 0%, rgba(0, 0, 0, .18) 42%, rgba(0, 0, 0, .38) 100%);
}

.hero-grain {
  position: absolute;
  inset: 0;
  background-image: url('/images/grain.png');
  background-size: 220px;
  opacity: .07;
  mix-blend-mode: overlay;
  pointer-events: none;
}

.hero-frame {
  position: absolute;
  top: calc(var(--header-h) + 18px);
  right: var(--pad);
  z-index: 2;
  font-size: 8px;
  letter-spacing: .16em;
  color: color-mix(in srgb, oldlace 46%, transparent);
}

.hero-copy,
.hero-ledger,
.hero-player,
.hero-frame {
  transition: opacity .55s ease;
}
.hero.is-intro .hero-copy,
.hero.is-intro .hero-ledger,
.hero.is-intro .hero-player,
.hero.is-intro .hero-frame {
  opacity: 0;
}

.hero-inner {
  position: absolute;
  left: 50%;
  bottom: calc(var(--gate-h) + 74px);
  z-index: 2;
  width: 100%;
  max-width: var(--maxw);
  transform: translateX(-50%);
  padding: 0 var(--pad);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: clamp(32px, 5vw, 80px);
}

.hero-copy { min-width: 0; max-width: 820px; }

.hero .eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 10px;
  letter-spacing: .16em;
  color: color-mix(in srgb, oldlace 78%, transparent);
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--acid);
  box-shadow: 0 0 0 4px color-mix(in srgb, orange 18%, transparent);
  animation: blink 1.8s steps(2) infinite;
}
@keyframes blink { 50% { opacity: .25; } }

.hero-title {
  margin: 20px 0 0;
  font-family: var(--font-cn);
  font-weight: 900;
  font-size: clamp(66px, 9vw, 128px);
  line-height: .9;
  letter-spacing: -.05em;
  text-wrap: nowrap;
}
.hero-title span { display: block; }
.hero-title mark {
  display: inline-block;
  padding: 0 .08em .04em;
  background: var(--acid);
  color: var(--ink);
  box-decoration-break: clone;
}

.hero-slogan {
  margin: 18px 0 0;
  font-family: var(--font-display);
  font-size: clamp(16px, 1.55vw, 22px);
  letter-spacing: .16em;
  color: color-mix(in srgb, oldlace 82%, transparent);
}

.hero-sub {
  max-width: 470px;
  margin: 12px 0 0;
  color: color-mix(in srgb, oldlace 64%, transparent);
  font-family: var(--font-cn);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}
.hero-ghost { background: color-mix(in srgb, black 24%, transparent); }

.hero-cta {
  min-height: 58px;
  padding: 9px 20px 10px;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}
.hero-cta-price {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  font-family: var(--font-display);
  line-height: .9;
}
.hero-cta-price small { font-size: 19px; }
.hero-cta-price b { font-size: 40px; font-weight: 400; line-height: .9; }
.hero-cta-label {
  margin-top: 2px;
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 500;
  letter-spacing: .08em;
  opacity: .88;
}

.hero-ledger {
  flex: none;
  width: min(340px, 32vw);
  padding: 14px 16px 12px;
  border: 1px solid color-mix(in srgb, oldlace 22%, transparent);
  background: color-mix(in srgb, black 58%, transparent);
  backdrop-filter: blur(6px);
}
.ledger-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 9px;
  border-bottom: 1px solid color-mix(in srgb, oldlace 16%, transparent);
  font-size: 7px;
  letter-spacing: .14em;
  color: color-mix(in srgb, oldlace 52%, transparent);
}
.ledger-head i {
  flex: 1;
  height: 1px;
  background: color-mix(in srgb, orange 46%, transparent);
}

.member-mini { margin: 12px 0 0; }
.mm-title {
  margin: 0;
  font-family: var(--font-cn);
  font-size: 24px;
  font-weight: 900;
  line-height: 1.12;
  letter-spacing: -.03em;
  color: var(--paper);
}
.mm-line {
  margin: 5px 0 0;
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 500;
  color: color-mix(in srgb, oldlace 62%, transparent);
}
.mm-qr {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: 14px;
}
.mm-qr-img {
  flex: none;
  width: 88px;
  height: 88px;
  padding: 5px;
  border-radius: 8px;
  background: #fff;
}
.mm-qr-copy { min-width: 0; }
.mm-qr-title {
  margin: 0;
  font-family: var(--font-cn);
  font-size: 13px;
  font-weight: 900;
  line-height: 1.35;
  color: var(--paper);
}
.mm-qr-sub {
  margin: 4px 0 0;
  font-family: var(--font-cn);
  font-size: 10.5px;
  font-weight: 500;
  line-height: 1.6;
  color: color-mix(in srgb, oldlace 58%, transparent);
}
.mm-search {
  margin: 10px 0 0;
  font-size: 7px;
  letter-spacing: .08em;
  color: var(--acid);
}

.ledger-cue {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0 0;
  padding-top: 9px;
  border-top: 1px solid color-mix(in srgb, oldlace 14%, transparent);
  font-size: 7px;
  letter-spacing: .12em;
  color: color-mix(in srgb, orange 74%, transparent);
}
.ledger-cue b {
  font-size: 13px;
  line-height: 1;
  animation: cue 1.8s var(--ease-gate) infinite;
}
@keyframes cue {
  0%, 100% { transform: translateY(-2px); opacity: .4; }
  50% { transform: translateY(2px); opacity: 1; }
}

.hero-player {
  position: absolute;
  left: 0;
  right: 0;
  bottom: var(--gate-h);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 12px var(--pad) 15px;
  border-top: 1px solid color-mix(in srgb, oldlace 16%, transparent);
  background: linear-gradient(0deg, rgba(0, 0, 0, .88), rgba(0, 0, 0, .28));
}
.player-toggle {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 78px;
  padding: 6px 10px;
  border: 1px solid color-mix(in srgb, oldlace 30%, transparent);
  background: color-mix(in srgb, black 34%, transparent);
  color: var(--paper);
  font-size: 8px;
  letter-spacing: .1em;
  transition: border-color .2s ease, color .2s ease, background .2s ease;
}
.player-toggle:hover {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}
.toggle-icon { font-size: 9px; letter-spacing: .04em; }

.player-time {
  flex: none;
  min-width: 30px;
  font-size: 8px;
  letter-spacing: .04em;
  color: color-mix(in srgb, oldlace 56%, transparent);
}

.player-track {
  position: relative;
  flex: 1;
  height: 3px;
  background: color-mix(in srgb, oldlace 18%, transparent);
  cursor: pointer;
  text-align: left;
}
.player-track i {
  display: block;
  height: 100%;
  background: var(--acid);
  transition: width .1s linear;
}
.player-track b {
  position: absolute;
  top: 50%;
  width: 7px;
  height: 7px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: var(--acid);
  box-shadow: 0 0 0 3px color-mix(in srgb, orange 20%, transparent);
}

.player-tag {
  flex: none;
  font-size: 7px;
  letter-spacing: .12em;
  color: color-mix(in srgb, oldlace 42%, transparent);
}

@media (max-width: 1100px) {
  .hero-inner { gap: 28px; }
  .hero-title { font-size: clamp(60px, 8.6vw, 104px); }
  .hero-ledger { width: 280px; padding: 12px; }
  .hero-cta-price b { font-size: 34px; }
}

@media (max-width: 900px) {
  .hero-frame { display: none; }
  .hero-inner {
    bottom: calc(var(--gate-h) + 66px);
    padding: 0 var(--pad);
  }
  .hero-copy { max-width: 100%; }
  .hero .eyebrow { font-size: 8px; letter-spacing: .1em; }
  .hero-title { margin-top: 14px; font-size: clamp(62px, 17vw, 88px); line-height: .94; }
  .hero-slogan { margin-top: 12px; font-size: 13px; letter-spacing: .1em; }
  .hero-sub { margin-top: 8px; font-size: 12px; line-height: 1.65; max-width: 330px; }
  .hero-actions { margin-top: 16px; }
  .hero-actions .btn { flex: 1; min-height: 38px; padding: 8px 10px; font-size: 9px; }
  .hero-actions .hero-cta { min-height: 52px; padding: 7px 12px 8px; }
  .hero-actions .hero-cta-price b { font-size: 30px; }
  .hero-actions .hero-cta-label { font-size: 7px; }
  .hero-ledger { display: none; }
  .hero-player { gap: 9px; padding: 9px var(--pad) 11px; }
  .player-toggle { min-width: 66px; padding: 5px 8px; font-size: 7px; }
  .player-tag { display: none; }
  .player-time { font-size: 7px; min-width: 26px; }
}

@media (max-height: 760px) and (min-width: 901px) {
  .hero-title { font-size: clamp(58px, 7.4vw, 96px); }
  .hero-sub { margin-top: 8px; }
  .hero-actions { margin-top: 16px; }
  .hero-cta { min-height: 52px; }
  .hero-cta-price b { font-size: 34px; }
  .hero-ledger { width: 300px; }
}

@media (max-width: 900px) {
  .hero .eyebrow { font-size: 9px; }
  .hero-sub { font-size: 12px; }
  .hero-actions .btn { font-size: 10px; }
  .player-toggle { font-size: 8.5px; }
  .player-time { font-size: 8px; }
}
</style>
