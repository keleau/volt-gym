<template>
  <div class="screen-inner stations-circuit" :class="{ 'is-on': active }" data-scrollable>
    <header class="stations-head">
      <p class="station-no mono">02 / STATIONS</p>
      <h2>三处<em>充电站</em></h2>
      <p class="station-note">选一家，价格和预约在下一步一次完成。以下为各工场现场实拍。</p>
    </header>

    <!-- 三站接线图：一条电流线串三个节点 -->
    <div class="grid-map" role="group" aria-label="三处充电站接线图，点击节点查看详情">
      <span class="grid-line" aria-hidden="true"></span>
      <button
        v-for="(venue, i) in venues"
        :key="venue.code"
        type="button"
        class="grid-node"
        :class="{ 'is-on': current === venue.code }"
        :style="{ '--i': i }"
        :aria-pressed="current === venue.code"
        :aria-label="`查看 ${venue.name} 详情`"
        @click="current = venue.code"
      >
        <span class="node-ring">
          <span class="node-dot" aria-hidden="true"></span>
        </span>
        <span class="node-name">{{ venue.name }}</span>
        <span class="node-code mono">{{ venue.code }}</span>
      </button>
    </div>

    <!-- 详情台：选中站点实拍 + 信息 -->
    <section class="station-detail" :key="current">
      <figure class="detail-photo">
        <img
          :src="currentVenue.image"
          :alt="`${currentVenue.name} · 场地实拍`"
          loading="lazy"
          decoding="async"
        />
        <span class="photo-shade" aria-hidden="true"></span>
        <span class="photo-code mono">{{ current }}</span>
        <figcaption class="photo-cap mono">{{ currentVenue.en }}</figcaption>
      </figure>
      <div class="detail-body">
        <p class="detail-name">{{ currentVenue.name }}</p>
        <p class="detail-address">{{ currentVenue.address }}</p>
        <p class="detail-metro mono">{{ currentVenue.metro }}</p>
        <div class="detail-foot">
          <span class="detail-hours mono">{{ currentVenue.hours }}</span>
          <button
            type="button"
            class="detail-go mono"
            @click="$emit('go', 3, { venue: current })"
          >去预约 <b aria-hidden="true">→</b></button>
        </div>
      </div>
    </section>

    <footer class="station-foot mono">
      <span><i aria-hidden="true"></i>三店通练</span>
      <span>无年卡 · 无推销</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { venues } from '../../../data/site.js'
import { selection } from '../../../services/selection.js'

defineProps({
  active: { type: Boolean, default: false },
})
defineEmits(['go'])

const saved = venues.some((venue) => venue.code === selection.venue)
const current = ref(saved ? selection.venue : venues[0].code)
const currentVenue = computed(() => venues.find((v) => v.code === current.value) || venues[0])

watch(
  () => selection.venue,
  (code) => {
    if (venues.some((venue) => venue.code === code)) current.value = code
  }
)

watch(current, (code) => {
  selection.venue = code
})
</script>

<style scoped>
.stations-circuit {
  color: var(--paper);
  gap: clamp(12px, 2vh, 20px);
  overflow-y: auto;
  scrollbar-width: thin;
}

/* —— 标题区 —— */
.stations-head { flex: none; }
.station-no {
  margin: 0;
  font-size: 10px;
  letter-spacing: .14em;
  color: var(--acid);
}
.stations-head h2 {
  margin: 8px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(46px, 5vw, 78px);
  font-weight: 900;
  line-height: .94;
  letter-spacing: -.06em;
}
.stations-head h2 em {
  font-style: normal;
  color: var(--acid);
}
.station-note {
  max-width: 500px;
  margin: 12px 0 0;
  font-family: var(--font-cn);
  font-size: 15px;
  line-height: 1.7;
  color: var(--ash);
}

/* —— 接线图 —— */
.grid-map {
  position: relative;
  flex: none;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 6px clamp(8px, 6vw, 90px) 0;
}
.grid-line {
  position: absolute;
  top: 46px;
  left: clamp(8px, 6vw, 90px);
  right: clamp(8px, 6vw, 90px);
  height: 1px;
  background: repeating-linear-gradient(90deg,
    color-mix(in srgb, var(--paper) 20%, transparent) 0 10px,
    transparent 10px 20px);
  overflow: hidden;
}
.grid-line::after {
  content: '';
  position: absolute;
  inset: -4px 0;
  background: linear-gradient(90deg,
    transparent 30%,
    color-mix(in srgb, var(--acid) 55%, transparent) 50%,
    transparent 70%);
  background-size: 220% 100%;
  background-position: 120% 0;
  animation: current-flow 2.6s linear infinite;
}
@keyframes current-flow {
  from { background-position: 120% 0; }
  to { background-position: -120% 0; }
}
@media (prefers-reduced-motion: reduce) {
  .grid-line::after { animation: none; }
}

.grid-node {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--paper);
  cursor: pointer;
  opacity: 0;
  transform: translateY(14px);
  transition:
    opacity .45s ease calc(var(--i) * 90ms + .1s),
    transform .6s var(--ease-gate) calc(var(--i) * 90ms + .1s);
}
.stations-circuit.is-on .grid-node { opacity: 1; transform: none; }
.grid-node:focus-visible { outline: 2px solid var(--acid); outline-offset: 4px; border-radius: 50%; }

.node-ring {
  position: relative;
  width: 84px;
  height: 84px;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--paper) 26%, transparent);
  border-radius: 50%;
  background: var(--panel);
  transition: border-color .2s ease, background .2s ease, box-shadow .2s ease;
}
.node-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--paper) 30%, transparent);
  transition: background .2s ease, box-shadow .2s ease, transform .2s ease;
}
.grid-node:hover .node-ring,
.grid-node:focus-visible .node-ring {
  border-color: color-mix(in srgb, var(--acid) 60%, transparent);
}
.grid-node:hover .node-dot { background: var(--acid); }
.grid-node.is-on .node-ring {
  border-color: var(--acid);
  background: color-mix(in srgb, var(--acid) 10%, var(--panel));
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--acid) 8%, transparent);
}
.grid-node.is-on .node-dot {
  width: 16px;
  height: 16px;
  background: var(--acid);
  box-shadow: 0 0 14px color-mix(in srgb, var(--acid) 70%, transparent);
}
.node-name {
  font-family: var(--font-cn);
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}
.node-code {
  font-size: 8px;
  letter-spacing: .14em;
  color: var(--ash);
}
.grid-node.is-on .node-code { color: var(--acid); }

/* —— 详情台 —— */
.station-detail {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--ink) 14%, transparent);
  background: var(--paper);
  color: var(--ink);
  animation: detail-in .45s var(--ease-gate) both;
}
@keyframes detail-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

.detail-photo {
  position: relative;
  flex: none;
  width: clamp(220px, 54%, 560px);
  margin: 0;
  overflow: hidden;
  background: var(--ink);
}
.detail-photo img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(.96) contrast(1.03) brightness(.95);
}
.photo-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(0deg, rgba(13, 11, 8, .6) 0%, rgba(13, 11, 8, 0) 44%);
}
.photo-code {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 3px 7px;
  background: var(--acid);
  color: var(--ink);
  font-size: 8px;
  letter-spacing: .1em;
}
.photo-cap {
  position: absolute;
  left: 14px;
  bottom: 13px;
  font-size: 8px;
  letter-spacing: .18em;
  color: color-mix(in srgb, var(--paper) 78%, transparent);
}

.detail-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: clamp(18px, 3vh, 30px);
}
.detail-name {
  margin: 0;
  font-family: var(--font-cn);
  font-size: clamp(34px, 3.6vw, 54px);
  font-weight: 900;
  line-height: .98;
  letter-spacing: -.05em;
}
.detail-address {
  margin: 0;
  font-family: var(--font-cn);
  font-size: 14px;
  line-height: 1.5;
  color: color-mix(in srgb, var(--ink) 66%, transparent);
}
.detail-metro {
  margin: 0;
  font-size: 9px;
  letter-spacing: .12em;
  color: #8A5200;
}
.detail-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid color-mix(in srgb, var(--ink) 12%, transparent);
}
.detail-hours {
  font-size: 9px;
  letter-spacing: .05em;
  color: color-mix(in srgb, var(--ink) 60%, transparent);
  white-space: nowrap;
}
.detail-go {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  font-size: 9px;
  letter-spacing: .12em;
  cursor: pointer;
  transition: background .18s ease, color .18s ease, border-color .18s ease;
}
.detail-go b {
  font-size: 14px;
  line-height: 1;
  color: var(--acid);
  transition: transform .18s ease;
}
.detail-go:hover,
.detail-go:focus-visible {
  background: var(--acid);
  border-color: var(--acid);
  color: var(--ink);
  outline: none;
}
.detail-go:hover b,
.detail-go:focus-visible b {
  color: var(--ink);
  transform: translateX(3px);
}

/* —— 底部条 —— */
.station-foot {
  flex: none;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  font-size: 8px;
  letter-spacing: .1em;
  color: var(--ash);
}
.station-foot span { display: inline-flex; align-items: center; gap: 7px; }
.station-foot i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--acid);
}

@media (max-height: 800px) and (min-width: 901px) {
  .stations-head h2 { font-size: clamp(40px, 4.4vw, 64px); }
  .station-note { margin-top: 9px; }
  .node-ring { width: 68px; height: 68px; }
  .grid-line { top: 38px; }
  .detail-body { padding: 16px 20px; gap: 6px; }
  .detail-name { font-size: 34px; }
  .detail-foot { margin-top: 6px; padding-top: 9px; }
}

@media (max-width: 900px) {
  .stations-head h2 { font-size: clamp(42px, 12vw, 56px); }
  .station-note { margin-top: 9px; font-size: 12px; }
  .grid-map { padding: 4px 2vw 0; gap: 6px; }
  .grid-line { top: 38px; left: 2vw; right: 2vw; }
  .node-ring { width: 64px; height: 64px; }
  .node-name { font-size: 14px; }
  .station-detail { flex-direction: column; }
  .detail-photo {
    width: 100%;
    height: clamp(150px, 26vh, 210px);
  }
  .detail-body { padding: 14px 16px 16px; gap: 6px; }
  .detail-name { font-size: 32px; }
  .detail-address { font-size: 12.5px; }
  .detail-foot { margin-top: 6px; padding-top: 9px; }
  .detail-go { padding: 9px 13px; }
  .station-foot { margin-top: 2px; }
}
</style>
