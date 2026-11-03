<template>
  <div class="screen-inner programs-circuit" :class="{ 'is-on': active }" data-scrollable>
    <header class="program-head">
      <div>
        <p class="program-no mono">03 / PROGRAMS</p>
        <h2>五门<em>电流课</em>。</h2>
      </div>
      <p class="program-note">强度从高到低。<br />点一门，下一步带入预约。</p>
    </header>

    <div class="course-field" role="group" aria-label="五门电流课，强度从高到低">
      <svg class="course-trace" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <path class="trace-glow" d="M38 8 L32 27 L52 47 L46 66 L64 82" />
        <path
          class="trace-line"
          pathLength="1"
          d="M38 8 L32 27 L52 47 L46 66 L64 82"
        />
        <path
          class="trace-pulse"
          pathLength="100"
          d="M38 8 L32 27 L52 47 L46 66 L64 82"
        />
        <path class="trace-tap" d="M38 8 h.75 M32 27 h-.75 M52 47 h.75 M46 66 h-.75 M64 82 h.75" />
      </svg>

      <button
        v-for="(program, i) in courses"
        :key="program.no"
        type="button"
        class="course-btn"
        :class="{ 'is-left': coursePos[program.no].side === 'left' }"
        :style="{
          '--x': coursePos[program.no].x,
          '--y': coursePos[program.no].y,
          '--fs': coursePos[program.no].fs,
          '--i': i,
        }"
        :aria-label="`选择 ${program.name}，去预约`"
        @click="$emit('go', 3, { program: program.no })"
      >
        <img
          class="c-pic"
          :src="program.img"
          :alt="`${program.name}课程现场`"
          loading="lazy"
          decoding="async"
        />
        <span class="c-text">
          <span class="c-top mono">
            <span class="c-no">{{ program.no }}</span>
            <span class="c-bars" aria-hidden="true">
              <i v-for="n in 3" :key="n" :class="{ on: n <= bars[program.meta.intensity] }"></i>
            </span>
            <span class="c-meta">{{ program.meta.intensity }} · {{ program.meta.duration }}</span>
          </span>
          <span class="c-name">{{ program.name }}</span>
          <span class="c-en mono">{{ program.en }}<i class="c-arrow" aria-hidden="true">→</i></span>
        </span>
      </button>
    </div>

    <footer class="program-foot mono">
      <span>FIVE TRACKS · 01—05</span>
      <span><i aria-hidden="true"></i>三店通用 · 小程序选课</span>
    </footer>
  </div>
</template>

<script setup>
import { featuredPrograms } from '../../../data/programs.js'

defineProps({
  active: { type: Boolean, default: false },
})
defineEmits(['go'])

const courses = featuredPrograms
const bars = { HIGH: 3, MID: 2, LOW: 1 }

const coursePos = {
  P01: { x: 38, y: 8, side: 'right', fs: 'clamp(48px, 5.2vw, 82px)' },
  P02: { x: 32, y: 27, side: 'left', fs: 'clamp(40px, 4.2vw, 66px)' },
  P03: { x: 52, y: 47, side: 'right', fs: 'clamp(34px, 3.4vw, 54px)' },
  P04: { x: 46, y: 66, side: 'left', fs: 'clamp(30px, 2.9vw, 46px)' },
  P05: { x: 64, y: 82, side: 'right', fs: 'clamp(26px, 2.4vw, 38px)' },
}
</script>

<style scoped>
.programs-circuit {
  color: var(--paper);
  gap: 0;
}

/* —— 标题区 —— */
.program-head {
  flex: none;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
}
.program-no {
  margin: 0;
  font-size: 10px;
  letter-spacing: .14em;
  color: color-mix(in srgb, #F59E0B 84%, transparent);
}
.program-head h2 {
  margin: 6px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(46px, 5.2vw, 78px);
  font-weight: 900;
  line-height: .94;
  letter-spacing: -.06em;
}
.program-head h2 em {
  font-style: normal;
  color: var(--acid);
}
.program-note {
  max-width: 320px;
  margin: 0 0 4px;
  font-family: var(--font-cn);
  font-size: 14px;
  line-height: 1.65;
  color: color-mix(in srgb, #F2EFE4 66%, transparent);
  text-align: right;
}

/* —— 接线场 —— */
.course-field {
  position: relative;
  flex: 1;
  min-height: 0;
  margin-top: 8px;
}
.course-field::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle,
    color-mix(in srgb, oldlace 12%, transparent) 1px,
    transparent 1.3px);
  background-size: 24px 24px;
  opacity: .34;
  -webkit-mask-image: linear-gradient(180deg, transparent, #000 10%, #000 90%, transparent);
  mask-image: linear-gradient(180deg, transparent, #000 10%, #000 90%, transparent);
  pointer-events: none;
}

.course-trace {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}
.trace-glow {
  fill: none;
  stroke: var(--acid);
  stroke-width: 5;
  stroke-opacity: .12;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}
.trace-line {
  fill: none;
  stroke: var(--acid);
  stroke-width: 1.1;
  stroke-opacity: .68;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
}
.programs-circuit.is-on .trace-line {
  animation: trace-draw 1.2s cubic-bezier(.4, 0, .2, 1) .2s forwards;
}
@keyframes trace-draw {
  to { stroke-dashoffset: 0; }
}
.trace-tap {
  fill: none;
  stroke: var(--acid);
  stroke-width: 1;
  stroke-opacity: .55;
  vector-effect: non-scaling-stroke;
}
.trace-pulse {
  fill: none;
  stroke: var(--acid);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-opacity: 0;
  vector-effect: non-scaling-stroke;
  stroke-dasharray: 6 94;
  stroke-dashoffset: 0;
  pointer-events: none;
}
.course-field:has(.course-btn:hover) .trace-pulse,
.course-field:has(.course-btn:focus-visible) .trace-pulse {
  stroke-opacity: .95;
  animation: trace-pulse 1.6s linear infinite;
}
@keyframes trace-pulse {
  from { stroke-dashoffset: 100; }
  to { stroke-dashoffset: 0; }
}

/* —— 课程节点 —— */
.course-btn {
  position: absolute;
  z-index: 1;
  left: calc(var(--x) * 1% - 4px);
  top: calc(var(--y) * 1% - 6px);
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 14px;
  padding: 0 0 0 14px;
  border: 0;
  background: none;
  color: var(--paper);
  text-align: left;
  cursor: pointer;
  opacity: 0;
  transform: translateY(14px);
  transition:
    opacity .4s ease calc(var(--i) * 90ms + .18s),
    transform .55s var(--ease-gate) calc(var(--i) * 90ms + .18s);
}
.programs-circuit.is-on .course-btn {
  opacity: 1;
  transform: none;
}
.course-btn.is-left {
  left: auto;
  right: calc((100 - var(--x)) * 1% - 4px);
  flex-direction: row-reverse;
  align-items: center;
  padding: 0 14px 0 0;
  text-align: right;
}

/* —— 课程小图 —— */
.c-pic {
  flex: none;
  width: clamp(64px, 6vw, 92px);
  aspect-ratio: 3 / 2;
  object-fit: cover;
  border-radius: 6px;
  filter: saturate(.82) contrast(1.06) brightness(.92);
  transition: filter .25s ease, transform .25s ease;
}
.course-btn:hover .c-pic,
.course-btn:focus-visible .c-pic {
  filter: saturate(.95) contrast(1.06) brightness(1.04);
  transform: scale(1.05);
}

.c-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  min-width: 0;
}
.course-btn.is-left .c-text {
  align-items: flex-end;
}
.course-btn::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--acid);
  box-shadow:
    0 0 0 4px color-mix(in srgb, orange 9%, transparent),
    0 0 14px color-mix(in srgb, orange 40%, transparent);
  transition: transform .2s ease, box-shadow .2s ease;
}
.course-btn.is-left::before {
  left: auto;
  right: 0;
}
.course-btn:hover::before,
.course-btn:focus-visible::before {
  transform: scale(1.4);
  box-shadow:
    0 0 0 6px color-mix(in srgb, orange 12%, transparent),
    0 0 22px color-mix(in srgb, orange 68%, transparent);
}
.course-btn:focus-visible {
  outline: 1px solid var(--acid);
  outline-offset: 8px;
}

.c-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  line-height: 12px;
  letter-spacing: .07em;
  color: color-mix(in srgb, oldlace 72%, transparent);
  white-space: nowrap;
}
.course-btn.is-left .c-top {
  justify-content: flex-end;
}
.c-no {
  color: var(--acid);
  letter-spacing: .06em;
}
.c-bars {
  display: inline-flex;
  align-items: flex-end;
  gap: 2px;
  height: 9px;
}
.c-bars i {
  display: block;
  width: 2px;
  height: 4px;
  background: color-mix(in srgb, oldlace 20%, transparent);
}
.c-bars i:nth-child(2) { height: 6px; }
.c-bars i:nth-child(3) { height: 9px; }
.c-bars i.on { background: var(--acid); }

.c-name {
  font-family: var(--font-cn);
  font-size: var(--fs);
  font-weight: 900;
  line-height: .95;
  letter-spacing: -.06em;
  color: var(--paper);
  white-space: nowrap;
  transition: color .2s ease;
}
.course-btn:hover .c-name,
.course-btn:focus-visible .c-name {
  color: var(--acid);
}

.c-en {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  line-height: 13px;
  letter-spacing: .15em;
  color: color-mix(in srgb, oldlace 68%, transparent);
  white-space: nowrap;
}
.c-arrow {
  font-style: normal;
  font-size: 12px;
  line-height: 1;
  color: color-mix(in srgb, orange 68%, transparent);
  transition: transform .2s ease, color .2s ease;
}
.course-btn:hover .c-arrow,
.course-btn:focus-visible .c-arrow {
  transform: translateX(4px);
  color: var(--acid);
}

/* —— 底条 —— */
.program-foot {
  flex: none;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding-top: 11px;
  border-top: 1px solid var(--border);
  font-size: 8px;
  letter-spacing: .1em;
  color: var(--ash);
}
.program-foot span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.program-foot i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--acid);
}

@media (max-height: 820px) and (min-width: 901px) {
  .program-head h2 { font-size: clamp(40px, 4.6vw, 64px); }
  .program-note { font-size: 12.5px; }
  .c-name { font-size: calc(var(--fs) * .86); }
  .program-foot { margin-top: 8px; padding-top: 8px; }
}

@media (prefers-reduced-motion: reduce) {
  .trace-line { stroke-dashoffset: 0; animation: none !important; }
  .trace-pulse { animation: none !important; stroke-opacity: 0 !important; }
  .course-btn { opacity: 1; transform: none; transition: none; }
}

@media (max-width: 900px) {
  .program-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding-bottom: 11px;
  }
  .program-head h2 { font-size: clamp(40px, 12vw, 54px); }
  .program-note {
    max-width: none;
    margin: 0;
    font-size: 12px;
    text-align: left;
  }

  .course-field {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: 6px;
    margin-top: 10px;
    padding: 6px 0;
  }
  .course-field::before { background-size: 18px 18px; }
  .course-field::after {
    content: '';
    position: absolute;
    left: 5px;
    top: 8px;
    bottom: 8px;
    width: 1px;
    background: linear-gradient(180deg,
      transparent,
      color-mix(in srgb, orange 74%, transparent) 10%,
      color-mix(in srgb, orange 74%, transparent) 90%,
      transparent);
  }
  .course-trace { display: none; }

  .course-btn,
  .course-btn.is-left {
    position: relative;
    left: auto;
    right: auto;
    top: auto;
    width: 100%;
    flex-direction: row;
    align-items: center;
    gap: 12px;
    padding: 0 0 0 26px;
    text-align: left;
  }
  .course-btn.is-left .c-text,
  .course-btn .c-text {
    align-items: flex-start;
    text-align: left;
  }
  .course-btn.is-left .c-top { justify-content: flex-start; }
  .c-pic { width: 56px; }
  .course-btn::before,
  .course-btn.is-left::before {
    left: 1px;
    right: auto;
    top: 2px;
  }
  .course-btn:nth-of-type(even) { padding-left: 42px; }
  .c-name { font-size: calc(var(--fs) * .9); }
  .c-top { font-size: 9px; gap: 7px; }
  .c-en { font-size: 9px; letter-spacing: .12em; gap: 7px; }
  .program-foot { margin-top: 8px; padding-top: 9px; font-size: 7px; }
}
</style>
