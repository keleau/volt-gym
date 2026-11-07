<template>
  <div class="volt-app" :data-step="chromeStep.id">
    <VoltHeader
      :steps="steps"
      :nav="navMain"
      :active="chromeIndex"
      :cta="3"
      :member="memberSession?.member || null"
      @go="handleGo"
      @community="goCommunity"
      @member="openMember"
    />

    <main class="stage">
      <section
        v-for="(panel, i) in steps"
        :key="panel.id"
        :ref="(el) => setScreenRef(el, i)"
        class="screen"
        :class="[`tone-${panel.tone}`, screenClass(i)]"
        :aria-hidden="isShown(i) ? 'false' : 'true'"
        :inert="!isShown(i)"
      >
        <StepHero
          v-if="i === 0"
          :active="isContentActive(i)"
          :intro="introActive"
          @go="handleGo"
        />
        <CinemaIntro
          v-if="i === 0 && introActive"
          @done="finishIntro"
        />
        <StepStations
          v-else-if="i === 1"
          :active="isContentActive(i)"
          @go="handleGo"
        />
        <StepPrograms
          v-else-if="i === 2"
          :active="isContentActive(i)"
          @go="handleGo"
        />
        <StepOffer
          v-else-if="i === 3"
          :active="isContentActive(i)"
          :preset="preset"
          @member="openMemberWithPhone"
        />
        <StepFooter
          v-else
          :active="isContentActive(i)"
          @go="handleGo"
          @community="goCommunity"
        />
      </section>

      <svg
        class="seam"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <g ref="seamGroup" class="seam-group">
          <path ref="seamGlow" class="seam-glow" d="" />
          <path ref="seamLine" class="seam-line" d="" />
        </g>
      </svg>
      <div ref="dissolveEl" class="dissolve" aria-hidden="true"></div>
    </main>

    <VoltRail :steps="steps" :active="chromeIndex" @go="handleGo" />
    <VoltGateFooter ref="gateRef" :steps="steps" :active="chromeIndex" />
    <MemberModal
      :open="memberOpen"
      :phone="memberPresetPhone"
      @close="closeMember"
      @change="onMemberChange"
    />

    <p class="sr-only" aria-live="polite">{{ announcement }}</p>
  </div>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue'
import VoltHeader from './VoltHeader.vue'
import VoltRail from './VoltRail.vue'
import VoltGateFooter from './VoltGateFooter.vue'
import CinemaIntro from './CinemaIntro.vue'
import StepHero from './steps/StepHero.vue'
import StepStations from './steps/StepStations.vue'
import StepPrograms from './steps/StepPrograms.vue'
import StepOffer from './steps/StepOffer.vue'
import StepFooter from './steps/StepFooter.vue'
import MemberModal from './member/MemberModal.vue'
import { getWebSession } from '../../services/api/index.js'
import { selection, setSelection } from '../../services/selection.js'
import { HOME_STEPS as steps, NAV_MAIN as navMain } from '../../data/steps.js'

const aliases = {
  hero: 0,
  live: 1,
  venues: 1,
  stations: 1,
  courses: 2,
  programs: 2,
  schedule: 3,
  pricing: 3,
  booking: 3,
  faq: 3,
  offer: 3,
  film: 0,
  video: 0,
  mini: 0,
  miniprogram: 0,
  footer: 4,
}

function indexFromHash(hash) {
  const id = decodeURIComponent(String(hash || '').replace(/^#/, ''))
  const direct = steps.findIndex((panel) => panel.id === id)
  if (direct >= 0) return direct
  return aliases[id] ?? 0
}

const initialIndex = indexFromHash(
  typeof window === 'undefined' ? '' : window.location.hash
)

const active = ref(initialIndex)
const pending = ref(null)
const chromeAt = ref(null)
const direction = ref(1)
const animating = ref(false)
const preset = selection
const INTRO_KEY = 'volt.intro.seen'
const introSeen = (() => {
  try {
    return typeof sessionStorage !== 'undefined' && sessionStorage.getItem(INTRO_KEY) === '1'
  } catch {
    return false
  }
})()
const introActive = ref(initialIndex === 0 && !introSeen)
let introTimer = 0

function finishIntro() {
  introActive.value = false
  clearTimeout(introTimer)
  try {
    sessionStorage.setItem(INTRO_KEY, '1')
  } catch {
    /* storage blocked; intro may replay, which is acceptable */
  }
}

if (introActive.value) {
  // 后台标签/减少动效时动画可能不触发，兜底解锁滚轮。
  introTimer = setTimeout(finishIntro, 5200)
}

const gateRef = ref(null)
const seamGroup = ref(null)
const seamGlow = ref(null)
const seamLine = ref(null)
const dissolveEl = ref(null)
const screenEls = []

const memberOpen = ref(false)
const memberSession = ref(getWebSession())
const memberPresetPhone = ref('')

function openMember() {
  memberPresetPhone.value = ''
  memberOpen.value = true
}

function openMemberWithPhone({ phone } = {}) {
  memberPresetPhone.value = phone || ''
  memberOpen.value = true
}

function closeMember() {
  memberOpen.value = false
  memberPresetPhone.value = ''
}

function syncMemberSession() {
  memberSession.value = getWebSession()
}

function onMemberChange() {
  syncMemberSession()
}

const chromeIndex = computed(() => chromeAt.value ?? active.value)
const chromeStep = computed(() => steps[chromeIndex.value] ?? steps[0])
const announcement = computed(
  () => `第 ${chromeStep.value.no} 屏，共 ${steps.length} 屏：${chromeStep.value.label}`
)

let raf = 0
let startedAt = 0
let lastNavAt = 0
let touchStart = null
let startTarget = null
let reduced = false

const TRANSITION_MS = 920
const AMBIENT_MS = 10900

function setScreenRef(el, index) {
  screenEls[index] = el
}

function isShown(index) {
  return index === active.value || index === pending.value
}
function isContentActive(index) {
  return index === active.value || index === pending.value
}
function screenClass(index) {
  return {
    'is-live': isShown(index),
    'is-active': pending.value === null && active.value === index,
    'is-incoming': pending.value === index,
    'is-outgoing': pending.value !== null && active.value === index,
  }
}

function easeInOutCubic(t) {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

function wavePoints(progress, dir, phase) {
  const count = 18
  const amplitude = Math.sin(Math.PI * progress) * 2.05
  const base = dir > 0 ? 100 - progress * 100 : progress * 100
  const points = []
  for (let i = 0; i <= count; i++) {
    const x = (i / count) * 100
    const y =
      base +
      Math.sin(i * 0.86 + phase) * amplitude +
      Math.sin(i * 1.62 - phase * 0.7) * amplitude * 0.34
    points.push({ x, y: Math.max(0, Math.min(100, y)) })
  }
  return points
}

function wavePolygon(progress, dir, phase) {
  const points = wavePoints(progress, dir, phase)
  const line = points.map((p) => `${p.x.toFixed(2)}% ${p.y.toFixed(2)}%`).join(', ')
  if (dir > 0) return `polygon(0% 100%, ${line}, 100% 100%)`
  return `polygon(0% 0%, 100% 0%, ${points
    .slice()
    .reverse()
    .map((p) => `${p.x.toFixed(2)}% ${p.y.toFixed(2)}%`)
    .join(', ')})`
}

function setSeam(progress, phase) {
  const points = wavePoints(progress, direction.value, phase)
  const d = `M ${points.map((p) => `${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' L ')}`
  if (seamGlow.value) seamGlow.value.setAttribute('d', d)
  if (seamLine.value) seamLine.value.setAttribute('d', d)
  if (seamGroup.value) {
    seamGroup.value.style.opacity = String(Math.sin(Math.PI * progress) * 0.95)
  }
}

function clearTransitionStyles() {
  for (const el of screenEls) {
    if (!el) continue
    el.style.clipPath = ''
    el.style.opacity = ''
    el.style.transform = ''
    el.style.filter = ''
  }
  if (dissolveEl.value) dissolveEl.value.style.opacity = '0'
  if (seamGroup.value) seamGroup.value.style.opacity = '0'
}

function writeHash(index) {
  if (typeof window === 'undefined') return
  history.replaceState(null, '', `#${steps[index].id}`)
}

function finishTransition(to) {
  cancelAnimationFrame(raf)
  raf = 0
  clearTransitionStyles()
  chromeAt.value = null
  pending.value = null
  active.value = to
  animating.value = false
  lastNavAt = performance.now()
  writeHash(to)
}

function tick(now) {
  const elapsed = now - startedAt
  const raw = Math.min(elapsed / TRANSITION_MS, 1)
  const progress = easeInOutCubic(raw)
  const phase = (elapsed / AMBIENT_MS) * Math.PI * 2
  const from = active.value
  const to = pending.value
  const incoming = screenEls[to]
  const outgoing = screenEls[from]

  if (incoming) incoming.style.clipPath = wavePolygon(progress, direction.value, phase)
  if (outgoing) {
    outgoing.style.opacity = String(1 - progress * 0.42)
    outgoing.style.transform = `translateY(${-direction.value * 22 * progress}px) scale(${1 - progress * 0.012})`
    outgoing.style.filter = `blur(${progress * 1.4}px)`
  }
  if (progress >= 0.5 && chromeAt.value !== to) chromeAt.value = to
  setSeam(progress, phase)
  if (dissolveEl.value) {
    dissolveEl.value.style.opacity = String(Math.sin(Math.PI * progress) * 0.17)
  }

  if (raw < 1) {
    raf = requestAnimationFrame(tick)
  } else {
    finishTransition(to)
  }
}

function goTo(index, payload) {
  if (index < 0 || index >= steps.length) return

  if (payload) setSelection(payload)

  if (index === active.value) return
  if (animating.value) return

  lastNavAt = performance.now()

  if (reduced) {
    clearTransitionStyles()
    chromeAt.value = null
    active.value = index
    pending.value = null
    writeHash(index)
    return
  }

  const from = active.value
  pending.value = index
  chromeAt.value = null
  direction.value = index > from ? 1 : -1
  animating.value = true

  nextTick(() => {
    const incoming = screenEls[index]
    const outgoing = screenEls[from]
    if (!incoming || !outgoing || pending.value !== index) {
      finishTransition(index)
      return
    }
    incoming.style.clipPath = wavePolygon(0, direction.value, 0)
    outgoing.style.opacity = '1'
    outgoing.style.transform = 'translateY(0) scale(1)'
    outgoing.style.filter = 'blur(0px)'
    if (dissolveEl.value) dissolveEl.value.style.opacity = '0'
    setSeam(0.001, 0)
    gateRef.value?.pulse()
    startedAt = performance.now()
    raf = requestAnimationFrame(tick)
  })
}

function handleGo(index, payload) {
  goTo(index, payload)
}

function goCommunity() {
  if (typeof window === 'undefined') return
  window.location.hash = 'community'
}

function onHashChange() {
  const id = decodeURIComponent(String(window.location.hash || '').replace(/^#\/?/, ''))
  if (id === 'community' || id === 'staff') return
  const index = indexFromHash(window.location.hash)
  if (index !== active.value) goTo(index)
}

function closestScrollable(el) {
  return el instanceof Element ? el.closest('[data-scrollable]') : null
}

function modalOpen() {
  return document.body.dataset.modalOpen === 'true'
}

function canScroll(el, delta) {
  if (!el) return false
  if (el.scrollHeight <= el.clientHeight + 2) return false
  const atTop = el.scrollTop <= 1
  const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 2
  return delta > 0 ? !atBottom : !atTop
}

function onWheel(event) {
  if (introActive.value) {
    event.preventDefault()
    return
  }
  if (modalOpen() || animating.value) {
    event.preventDefault()
    return
  }
  const scrollable = closestScrollable(event.target)
  if (scrollable && canScroll(scrollable, event.deltaY)) return
  event.preventDefault()
  if (Math.abs(event.deltaY) < 3) return
  const now = performance.now()
  if (now - lastNavAt < 520) return
  lastNavAt = now
  goTo(active.value + (event.deltaY > 0 ? 1 : -1))
}

function onKeydown(event) {
  if (introActive.value || modalOpen()) return
  const tag = event.target?.tagName
  if (['INPUT', 'SELECT', 'TEXTAREA'].includes(tag) || event.target?.isContentEditable) return
  if (event.key === 'ArrowDown' || event.key === 'PageDown') {
    event.preventDefault()
    goTo(active.value + 1)
  } else if (event.key === 'ArrowUp' || event.key === 'PageUp') {
    event.preventDefault()
    goTo(active.value - 1)
  } else if (event.key === ' ' && !['BUTTON', 'A'].includes(tag)) {
    event.preventDefault()
    goTo(active.value + 1)
  } else if (event.key === 'Home') {
    event.preventDefault()
    goTo(0)
  } else if (event.key === 'End') {
    event.preventDefault()
    goTo(steps.length - 1)
  }
}

function onTouchStart(event) {
  if (event.touches.length !== 1) {
    touchStart = null
    return
  }
  touchStart = { x: event.touches[0].clientX, y: event.touches[0].clientY }
  startTarget = event.target
}

function onTouchEnd(event) {
  if (!touchStart || introActive.value || modalOpen()) return
  const dx = touchStart.x - event.changedTouches[0].clientX
  const dy = touchStart.y - event.changedTouches[0].clientY
  const target = startTarget
  touchStart = null
  startTarget = null
  if (Math.abs(dy) < 52 || Math.abs(dx) > Math.abs(dy) * 0.9) return
  const scrollable = closestScrollable(target)
  if (scrollable && canScroll(scrollable, dy)) return
  goTo(active.value + (dy > 0 ? 1 : -1))
}

function onResize() {
  // The clip is percentage-based; clearing it avoids a stale mid-transition path.
  if (!animating.value) clearTransitionStyles()
}

onMounted(() => {
  reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.addEventListener('volt:session-changed', syncMemberSession)
  window.addEventListener('volt:session-expired', syncMemberSession)
  window.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchend', onTouchEnd, { passive: true })
  window.addEventListener('resize', onResize)
  window.addEventListener('hashchange', onHashChange)
  if (initialIndex !== 0) writeHash(initialIndex)

  if (import.meta.env.DEV) {
    window.__volt = {
      goTo,
      state: () => ({
        active: active.value,
        pending: pending.value,
        animating: animating.value,
      }),
    }
  }
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  clearTimeout(introTimer)
  window.removeEventListener('volt:session-changed', syncMemberSession)
  window.removeEventListener('volt:session-expired', syncMemberSession)
  window.removeEventListener('wheel', onWheel)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('touchstart', onTouchStart)
  window.removeEventListener('touchend', onTouchEnd)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('hashchange', onHashChange)
  if (import.meta.env.DEV) delete window.__volt
})
</script>

<style scoped>
.seam {
  position: fixed;
  inset: 0;
  z-index: 90;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
}
.seam-group { opacity: 0; }
.seam path {
  fill: none;
  vector-effect: non-scaling-stroke;
}
.seam-glow {
  stroke: orange;
  stroke-width: 16;
  opacity: .26;
  filter: blur(3px);
}
.seam-line {
  stroke: orange;
  stroke-width: 1.8;
  opacity: .98;
}

.dissolve {
  position: fixed;
  inset: 0;
  z-index: 85;
  pointer-events: none;
  opacity: 0;
  background:
    repeating-linear-gradient(
      0deg,
      transparent 0 3px,
      color-mix(in srgb, whitesmoke 7%, transparent) 3px 4px
    ),
    radial-gradient(
      circle at 50% 50%,
      color-mix(in srgb, orange 10%, transparent),
      transparent 55%
    );
  mix-blend-mode: soft-light;
  will-change: opacity;
}

.screen.is-incoming { will-change: clip-path; }
</style>
