<template>
  <div class="cinema" :class="{ open }" role="presentation" aria-hidden="true">
    <div class="cinema-half cinema-left">
      <div class="cinema-layer">
        <span class="cinema-eyebrow mono">VOLTAGE FITNESS / GUANGZHOU</span>
        <div class="cinema-logo">
          <span class="cinema-word">VOLT</span>
          <span class="cinema-word slashes">//</span>
        </div>
        <span class="cinema-foot mono">PLUG IN. SWEAT. REPEAT. · EST. 2026 · 03 STATIONS</span>
      </div>
    </div>

    <div class="cinema-half cinema-right">
      <div class="cinema-layer">
        <span class="cinema-eyebrow mono">VOLTAGE FITNESS / GUANGZHOU</span>
        <div class="cinema-logo">
          <span class="cinema-word">VOLT</span>
          <span class="cinema-word slashes">//</span>
        </div>
        <span class="cinema-foot mono">PLUG IN. SWEAT. REPEAT. · EST. 2026 · 03 STATIONS</span>
      </div>
    </div>

    <i class="cinema-glow" aria-hidden="true"></i>
    <i class="cinema-bolt" aria-hidden="true"></i>
    <p class="cinema-note mono">THE CURRENT IS ON</p>
    <audio ref="crackleEl" src="/media/electric-crackle.wav" preload="auto"></audio>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits(['done'])
const open = ref(false)
const timers = []
const crackleEl = ref(null)
let blocked = false
let unlock = null

function playCrackle(volume) {
  const audio = crackleEl.value
  if (!audio) return
  audio.volume = volume
  audio.currentTime = 0
  const promise = audio.play()
  if (promise) promise.catch(() => { blocked = true })
}

onMounted(() => {
  timers.push(
    window.setTimeout(() => {
      open.value = true
    }, 1600),
    window.setTimeout(() => {
      emit('done')
    }, 3050),
    // 闪电点亮瞬间
    window.setTimeout(() => playCrackle(0.38), 320),
    // 幕布展开、通电完成
    window.setTimeout(() => playCrackle(0.2), 1950)
  )

  unlock = () => {
    if (blocked && !open.value) playCrackle(0.3)
  }
  window.addEventListener('pointerdown', unlock, { once: true })
  window.addEventListener('keydown', unlock, { once: true })
})

onBeforeUnmount(() => {
  timers.forEach((timer) => window.clearTimeout(timer))
  window.removeEventListener('pointerdown', unlock)
  window.removeEventListener('keydown', unlock)
})
</script>

<style scoped>
.cinema {
  position: absolute;
  inset: 0;
  z-index: 40;
  overflow: hidden;
  background: transparent;
}

.cinema-half {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 50%;
  overflow: hidden;
  background: var(--ink);
  transition: transform 1.2s cubic-bezier(.55, .02, .25, 1);
  will-change: transform;
}
.cinema-left { left: 0; }
.cinema-right { right: 0; }

.cinema-half::before {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    transparent 0 4px,
    color-mix(in srgb, orange 5%, transparent) 4px 5px
  );
  pointer-events: none;
}

.cinema-half::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(180deg, transparent, var(--acid) 22%, var(--acid) 78%, transparent);
  box-shadow: 0 0 22px 3px color-mix(in srgb, orange 46%, transparent);
  opacity: 0;
  transition: opacity .25s ease;
}
.cinema-left::after { right: 0; }
.cinema-right::after { left: 0; }
.cinema.open .cinema-half::after { opacity: .9; }

.cinema.open .cinema-left {
  transform: translate3d(-101%, -2.2%, 0) rotate(-.5deg);
}
.cinema.open .cinema-right {
  transform: translate3d(101%, 2.2%, 0) rotate(.5deg);
}

.cinema-layer {
  position: absolute;
  top: var(--header-h);
  bottom: var(--gate-h);
  left: 0;
  width: 100vw;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(14px, 2.4vh, 28px);
  padding: 0 var(--pad);
  text-align: center;
}
.cinema-right .cinema-layer { left: -50vw; }

.cinema-logo {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: .035em;
  white-space: nowrap;
}

.cinema-word {
  display: block;
  font-family: var(--font-display);
  font-size: clamp(100px, 19vw, 360px);
  line-height: .78;
  letter-spacing: -.02em;
  color: var(--paper);
  text-shadow: 0 0 42px color-mix(in srgb, orange 28%, transparent);
  animation:
    strike 1.1s cubic-bezier(.22, 1, .36, 1) both,
    ignite 1.5s ease .08s both;
}
.cinema-word.slashes {
  color: var(--acid);
  text-shadow:
    0 0 18px color-mix(in srgb, orange 54%, transparent),
    0 0 48px color-mix(in srgb, orange 22%, transparent);
  animation:
    strike 1.1s cubic-bezier(.22, 1, .36, 1) .06s both,
    slash-charge 1.5s ease .08s both;
}

@keyframes strike {
  0% { opacity: 0; transform: translateY(16px); filter: blur(6px); }
  100% { opacity: 1; transform: translateY(0); filter: blur(0); }
}
@keyframes ignite {
  0%, 8% { color: #2b2014; text-shadow: none; }
  16% { color: var(--acid); text-shadow: 0 0 34px color-mix(in srgb, orange 78%, transparent); }
  21% { color: #1b130b; text-shadow: none; }
  34% { color: var(--acid); text-shadow: 0 0 44px color-mix(in srgb, orange 84%, transparent); }
  48% { color: var(--paper); text-shadow: 0 0 30px color-mix(in srgb, orange 42%, transparent); }
  100% { color: var(--paper); text-shadow: 0 0 42px color-mix(in srgb, orange 28%, transparent); }
}
@keyframes slash-charge {
  0%, 10% { opacity: .25; filter: brightness(.6); }
  38% { opacity: 1; filter: brightness(1.25); }
  100% { opacity: 1; filter: brightness(1); }
}

.cinema-eyebrow,
.cinema-foot {
  font-size: 9px;
  letter-spacing: .22em;
  color: color-mix(in srgb, oldlace 52%, transparent);
  animation: fade 1s ease .3s both;
}
.cinema-foot { color: color-mix(in srgb, orange 72%, transparent); }

@keyframes fade {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.cinema-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    circle at 50% 50%,
    color-mix(in srgb, orange 13%, transparent),
    transparent 52%
  );
  mix-blend-mode: screen;
}

.cinema-bolt {
  position: absolute;
  top: 50%;
  left: 0;
  width: min(680px, 64vw);
  height: 2px;
  pointer-events: none;
  transform: translate(-120%, -50%) scaleX(.4);
  background: linear-gradient(
    90deg,
    transparent,
    color-mix(in srgb, orange 72%, transparent),
    #fff3d0,
    var(--acid),
    transparent
  );
  box-shadow:
    0 0 14px color-mix(in srgb, orange 62%, transparent),
    0 0 46px color-mix(in srgb, orange 34%, transparent);
  mix-blend-mode: screen;
  animation: bolt 1.55s cubic-bezier(.2, .7, .2, 1) .08s both;
}
@keyframes bolt {
  0% { transform: translate(-120%, -50%) scaleX(.35); opacity: 0; }
  12% { opacity: 1; }
  56% { opacity: 1; }
  100% { transform: translate(260%, -50%) scaleX(1); opacity: 0; }
}

.cinema-note {
  position: absolute;
  left: 50%;
  bottom: calc(var(--gate-h) + 18px);
  transform: translateX(-50%);
  margin: 0;
  font-size: 8px;
  letter-spacing: .36em;
  color: color-mix(in srgb, oldlace 40%, transparent);
  white-space: nowrap;
  transition: opacity .2s ease;
}
.cinema.open .cinema-note { opacity: 0; }

@media (max-width: 900px) {
  .cinema-layer {
    gap: 12px;
    padding: 0 14px;
  }
  .cinema-word { font-size: clamp(60px, 17vw, 92px); }
  .cinema-eyebrow { font-size: 6.5px; line-height: 1.6; letter-spacing: .16em; }
  .cinema-foot { font-size: 6px; letter-spacing: .1em; }
  .cinema-note { font-size: 6.5px; letter-spacing: .2em; bottom: calc(var(--gate-h) + 12px); }
}
</style>
