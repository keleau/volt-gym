<template>
  <section class="cm-hero">
    <div class="cm-inner cm-hero-inner">
      <div class="cm-hero-copy" data-reveal>
        <p class="cm-kicker mono"><span class="dot" aria-hidden="true"></span>VOLT COMMUNITY / 电压社区</p>
        <h1 class="cm-title"><span>一起</span><span><mark>充能</mark></span></h1>
        <p class="cm-sub">训练不是独走。打卡、冲榜、周末对练，共享同一股电流。</p>

        <div class="cm-hero-actions">
          <button type="button" class="btn btn-acid" @click="$emit('join')">¥{{ brand.trialPrice }} 加入下一场</button>
          <button type="button" class="btn cm-ghost" @click="$emit('events')">看本周活动</button>
        </div>

        <dl class="cm-stats">
          <div v-for="stat in stats" :key="stat.en">
            <dt>{{ stat.value }}</dt><dd>{{ stat.label }}</dd><em class="mono">{{ stat.en }}</em>
          </div>
        </dl>
      </div>

      <div class="cm-collage" data-reveal>
        <figure class="collage-a">
          <img src="/images/hero-02-960.webp" alt="社员在拳馆训练" />
          <figcaption class="mono">LIVE / SAT 19:08</figcaption>
        </figure>
        <figure class="collage-b">
          <img src="/images/gym-space-960.webp" alt="天河工场训练空间" />
        </figure>
        <figure class="collage-c">
          <img src="/images/coach-01-960.webp" alt="VOLT 教练" />
        </figure>
        <span class="collage-stamp mono">COMMUNITY<br />GZ · 2026</span>
      </div>
    </div>

    <div class="cm-marquee" aria-hidden="true">
      <div class="cm-marquee-track">
        <span>一起充能</span><i>//</i><span>TRAIN TOGETHER</span><i>//</i><span>03 STATIONS</span><i>//</i>
        <span>一起充能</span><i>//</i><span>TRAIN TOGETHER</span><i>//</i><span>03 STATIONS</span><i>//</i>
      </div>
    </div>
  </section>
</template>

<script setup>
import { brand } from '../../../data/site.js'

defineProps({
  stats: { type: Array, required: true },
})
defineEmits(['join', 'events'])
</script>

<style scoped>
.cm-hero {
  position: relative;
  padding-top: calc(var(--header-h) + clamp(42px, 7vh, 84px));
  padding-bottom: 0;
  overflow: hidden;
}
.cm-hero::before {
  content: '';
  position: absolute;
  top: 10%;
  left: 30%;
  width: 54vw;
  height: 54vw;
  max-width: 760px;
  max-height: 760px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, orange 12%, transparent), transparent 66%);
  pointer-events: none;
}

.cm-hero-inner {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(360px, .98fr);
  align-items: center;
  gap: clamp(32px, 6vw, 96px);
  padding-bottom: clamp(42px, 7vh, 78px);
}

.cm-kicker {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  font-size: 9px;
  letter-spacing: .16em;
  color: var(--acid);
}
.cm-kicker .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--acid);
  box-shadow: 0 0 0 4px color-mix(in srgb, orange 16%, transparent);
}

.cm-title {
  margin: 20px 0 0;
  font-family: var(--font-cn);
  font-weight: 900;
  font-size: clamp(76px, 10.5vw, 150px);
  line-height: .9;
  letter-spacing: -.05em;
}
.cm-title span { display: block; }
.cm-title mark {
  display: inline-block;
  padding: 0 .08em .04em;
  background: var(--acid);
  color: var(--ink);
}

.cm-sub {
  max-width: 440px;
  margin: 22px 0 0;
  font-family: var(--font-cn);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.8;
  color: color-mix(in srgb, oldlace 62%, transparent);
  text-wrap: pretty;
}

.cm-hero-actions { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 24px; }
.cm-ghost { background: color-mix(in srgb, #F2EFE4 6%, transparent); }

.cm-stats {
  display: flex;
  gap: clamp(26px, 4vw, 52px);
  margin: 30px 0 0;
  padding-top: 18px;
  border-top: 1px solid color-mix(in srgb, oldlace 14%, transparent);
}
.cm-stats div { display: flex; flex-direction: column; gap: 2px; }
.cm-stats dt {
  font-family: var(--font-display);
  font-size: clamp(30px, 3.2vw, 44px);
  font-weight: 400;
  line-height: 1;
  color: var(--paper);
}
.cm-stats dd { margin: 3px 0 0; font-family: var(--font-cn); font-size: 10px; font-weight: 700; }
.cm-stats em { font-size: 6.5px; font-style: normal; letter-spacing: .1em; color: color-mix(in srgb, oldlace 60%, transparent); }

.cm-collage {
  position: relative;
  display: grid;
  grid-template-columns: 1.18fr .82fr;
  grid-template-rows: 1fr 1fr;
  gap: 7px;
  height: min(54vh, 470px);
  min-height: 340px;
}
.cm-collage figure { position: relative; margin: 0; overflow: hidden; background: var(--ink); }
.cm-collage img { width: 100%; height: 100%; object-fit: cover; filter: saturate(.8) contrast(1.06) brightness(.86); transition: transform .8s var(--ease-gate), filter .4s ease; }
.cm-collage figure:hover img { transform: scale(1.04); filter: saturate(1) brightness(.96); }
.collage-a { grid-row: 1 / 3; }
.collage-a img { object-position: center 30%; }
.collage-b img { object-position: center 55%; }
.collage-c img { object-position: center 20%; }
.collage-a figcaption {
  position: absolute;
  left: 10px;
  bottom: 10px;
  padding: 4px 6px;
  background: var(--ink);
  color: var(--acid);
  font-size: 6px;
  letter-spacing: .1em;
}
.collage-stamp {
  position: absolute;
  right: -6px;
  top: 12px;
  padding: 6px 8px;
  border: 1px solid var(--acid);
  background: var(--ink);
  font-size: 6px;
  line-height: 1.45;
  letter-spacing: .1em;
  color: var(--acid);
  transform: rotate(3deg);
}

.cm-marquee {
  position: relative;
  overflow: hidden;
  border-top: 1px solid var(--acid);
  border-bottom: 1px solid var(--acid);
  background: var(--acid);
  color: var(--ink);
}
.cm-marquee-track {
  display: flex;
  align-items: center;
  gap: 18px;
  width: max-content;
  padding: 9px 0;
  animation: marquee 28s linear infinite;
}
.cm-marquee span {
  font-family: var(--font-display);
  font-size: clamp(18px, 2vw, 28px);
  letter-spacing: .04em;
  white-space: nowrap;
}
.cm-marquee i { font-style: normal; font-family: var(--font-mono); font-size: 12px; }
@keyframes marquee {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@media (max-width: 1100px) {
  .cm-hero-inner { grid-template-columns: 1fr .9fr; gap: 34px; }
  .cm-title { font-size: clamp(68px, 10vw, 120px); }
  .cm-collage { height: 400px; min-height: 300px; }
}

@media (max-width: 900px) {
  .cm-hero { padding-top: calc(var(--header-h) + 34px); }
  .cm-hero-inner { display: flex; flex-direction: column; align-items: stretch; gap: 28px; padding-bottom: 36px; }
  .cm-title { font-size: clamp(64px, 18vw, 96px); }
  .cm-sub { margin-top: 16px; font-size: 12px; }
  .cm-hero-actions { margin-top: 18px; }
  .cm-hero-actions .btn { flex: 1; }
  .cm-stats { gap: 22px; margin-top: 22px; padding-top: 14px; }
  .cm-stats dt { font-size: 32px; }
  .cm-collage { height: 300px; min-height: 260px; order: -1; }
  .cm-collage figure:nth-child(2), .cm-collage figure:nth-child(3) { display: none; }
  .cm-collage { display: block; }
  .collage-a { height: 300px; }
  .collage-stamp { right: 8px; }
  .cm-marquee-track { padding: 7px 0; gap: 12px; }
  .cm-marquee span { font-size: 20px; }
}

@media (prefers-reduced-motion: reduce) {
  .cm-marquee-track { animation: none; }
}
</style>
