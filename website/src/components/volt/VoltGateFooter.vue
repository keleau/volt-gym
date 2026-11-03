<template>
  <footer class="gate" aria-label="页脚">
    <canvas ref="canvasEl" class="gate-canvas" aria-hidden="true"></canvas>
    <div class="gate-scrim" aria-hidden="true"></div>

    <div class="gate-bar">
      <div class="gate-brand">
        <span class="mark">VOLT<span>//</span></span>
      </div>

      <div class="gate-contact">
        <span class="contact-label mono">CONTACT / 联系我们</span>
        <a class="phone mono" href="tel:4009006688">400-900-6688</a>
      </div>

      <div class="gate-meta">
        <span class="page" :aria-label="`第 ${step.no} 屏，共 ${total} 屏`">
          <b>{{ step.no }}</b><i>/ {{ total }}</i>
        </span>
      </div>
    </div>

    <div class="gate-progress" aria-hidden="true"><i :style="{ width: progress }"></i></div>
  </footer>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  steps: { type: Array, required: true },
  active: { type: Number, default: 0 },
})

const canvasEl = ref(null)
const step = computed(() => props.steps[props.active] ?? props.steps[0])
const total = computed(() => String(props.steps.length).padStart(2, '0'))
const progress = computed(() => `${((props.active + 1) / props.steps.length) * 100}%`)

// 10.9s is the ambient current cycle; a gate crossing stays under 1s.
const AMBIENT_CYCLE = 10900
const waves = [
  { y: 0.2, amp: 0.085, freq: 0.018, speed: 1.0, color: 'orange', alpha: 0.7, line: 1.2 },
  { y: 0.33, amp: 0.055, freq: 0.026, speed: -1.5, color: 'oldlace', alpha: 0.2, line: 1 },
  { y: 0.46, amp: 0.075, freq: 0.013, speed: 1.8, color: 'orange', alpha: 0.42, line: 1 },
  { y: 0.58, amp: 0.045, freq: 0.031, speed: -0.8, color: 'dimgray', alpha: 0.6, line: 1 },
  { y: 0.7, amp: 0.07, freq: 0.022, speed: 2.2, color: 'oldlace', alpha: 0.16, line: 1 },
  { y: 0.83, amp: 0.05, freq: 0.015, speed: -2.0, color: 'orange', alpha: 0.28, line: 1 },
]

let ctx = null
let raf = 0
let dpr = 1
let width = 0
let height = 0
let surgeStart = 0
let reduced = false
let ro = null

function resize() {
  const canvas = canvasEl.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  width = rect.width
  height = rect.height
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = Math.max(1, Math.round(width * dpr))
  canvas.height = Math.max(1, Math.round(height * dpr))
  ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  draw(performance.now())
}

function draw(now) {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  const t = ((now % AMBIENT_CYCLE) / AMBIENT_CYCLE) * Math.PI * 2
  const age = surgeStart ? (now - surgeStart) / 1000 : 99
  const boost = age < 1.4 ? 1 + 2.6 * Math.exp(-age * 3.4) : 1

  for (let i = 0; i < waves.length; i++) {
    const wave = waves[i]
    ctx.beginPath()
    for (let x = 0; x <= width; x += 4) {
      const y =
        height * wave.y +
        Math.sin(x * wave.freq + t * wave.speed + i * 1.7) * height * wave.amp * boost +
        Math.sin(x * wave.freq * 2.35 - t * wave.speed * 0.72) * height * wave.amp * 0.34
      if (x === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.globalAlpha = wave.alpha
    ctx.strokeStyle = wave.color
    ctx.lineWidth = wave.line
    ctx.stroke()
  }

  if (age < 1.2) {
    const x = Math.min(age / 1.2, 1) * width
    const grad = ctx.createLinearGradient(x - 80, 0, x + 80, 0)
    grad.addColorStop(0, 'transparent')
    grad.addColorStop(0.5, 'orange')
    grad.addColorStop(1, 'transparent')
    ctx.globalAlpha = 0.26
    ctx.fillStyle = grad
    ctx.fillRect(x - 80, 0, 160, height)
  }

  ctx.globalAlpha = 1
}

function loop(now) {
  draw(now)
  raf = requestAnimationFrame(loop)
}

function start() {
  if (reduced || raf) return
  raf = requestAnimationFrame(loop)
}

function stop() {
  cancelAnimationFrame(raf)
  raf = 0
}

function pulse() {
  if (reduced) return
  surgeStart = performance.now()
  start()
}

function onVisibility() {
  if (document.hidden) stop()
  else start()
}

function onResize() {
  resize()
}

onMounted(() => {
  reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  resize()
  if (!reduced) start()
  window.addEventListener('resize', onResize)
  document.addEventListener('visibilitychange', onVisibility)
  if (typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(onResize)
    if (canvasEl.value) ro.observe(canvasEl.value)
  }
})

onBeforeUnmount(() => {
  stop()
  window.removeEventListener('resize', onResize)
  document.removeEventListener('visibilitychange', onVisibility)
  ro?.disconnect()
})

defineExpose({ pulse })
</script>

<style scoped>
.gate {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 110;
  height: var(--gate-h);
  background: var(--ink);
  border-top: 1px solid var(--line-dark);
  color: var(--paper);
  overflow: hidden;
}

.gate-canvas,
.gate-scrim {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.gate-canvas { display: block; }
.gate-scrim {
  background:
    linear-gradient(180deg, color-mix(in srgb, black 30%, transparent), color-mix(in srgb, black 78%, transparent)),
    repeating-linear-gradient(90deg, transparent 0 79px, color-mix(in srgb, whitesmoke 4%, transparent) 79px 80px);
  pointer-events: none;
}

.gate-bar {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: var(--maxw);
  height: 100%;
  margin: 0 auto;
  padding: 0 var(--pad);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.gate-brand { display: flex; align-items: baseline; gap: 14px; min-width: 0; }
.gate-brand .mark {
  font-family: var(--font-display);
  font-size: 21px;
  letter-spacing: .04em;
  white-space: nowrap;
}
.gate-brand .mark span {
  padding: 0 3px;
  background: var(--acid);
  color: var(--ink);
}

.gate-contact {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  font-size: 9px;
  color: var(--ash);
}
.contact-label { font-size: 8px; letter-spacing: .08em; color: var(--paper); }
.phone { font-family: var(--font-mono); font-size: 10px; color: var(--paper); letter-spacing: .05em; transition: color .2s ease; }
.phone:hover { color: var(--acid); }

.gate-meta { display: flex; align-items: baseline; gap: 20px; }

.page {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font-family: var(--font-display);
  line-height: 1;
  color: var(--acid);
  white-space: nowrap;
}
.page b { font-size: 26px; font-weight: 400; letter-spacing: .02em; }
.page i { font-size: 10px; font-style: normal; color: var(--ash); letter-spacing: .06em; }

.gate-progress {
  position: absolute;
  top: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: color-mix(in srgb, whitesmoke 10%, transparent);
  z-index: 3;
}
.gate-progress i {
  display: block;
  height: 100%;
  background: var(--acid);
  box-shadow: 0 0 12px color-mix(in srgb, orange 55%, transparent);
  transition: width .55s var(--ease-gate);
}

@media (max-width: 900px) {
  .contact-label { display: none; }
  .gate-bar { gap: 12px; }
  .gate-contact { gap: 0; }
  .phone { font-size: 10px; }
  .page b { font-size: 23px; }
}
</style>
