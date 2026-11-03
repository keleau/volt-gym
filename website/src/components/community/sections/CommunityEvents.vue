<template>
  <section class="cm-events tone-paper">
    <div class="cm-inner">
      <div class="cm-section-head" data-reveal>
        <div>
          <p class="cm-no mono">04 / EVENTS</p>
          <h2>下一场，一起练</h2>
        </div>
        <p class="cm-note">入群报名 · 免费参加 · 迟到 10 分钟名额让出</p>
      </div>

      <div class="event-grid">
        <article v-for="event in events" :key="event.id" class="event-card" data-reveal>
          <div class="event-date">
            <strong>{{ event.day }}</strong>
            <span class="mono">{{ event.month }}</span>
          </div>
          <div class="event-main">
            <p class="mono event-tag">{{ event.tag }}</p>
            <h3>{{ event.title }}</h3>
            <p class="event-desc">{{ event.desc }}</p>
            <p class="mono event-meta">{{ event.time }} · {{ event.venue }}</p>
            <div class="event-cap">
              <span class="event-bar" aria-hidden="true"><i :style="{ width: `${event.cap ? (event.signed / event.cap) * 100 : 0}%` }"></i></span>
              <em class="mono">{{ event.signed }}/{{ event.cap }}</em>
            </div>
          </div>
          <button
            type="button"
            class="event-cta mono"
            :disabled="isSigned(event) || event.soldOut"
            @click="$emit('open', event)"
          >
            {{ isSigned(event) ? '已报名' : event.soldOut ? '已满' : '报名 →' }}
          </button>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup>
const props = defineProps({
  events: { type: Array, required: true },
  signedIds: { type: Array, default: () => [] },
})
defineEmits(['open'])

function isSigned(event) {
  return Boolean(event?.mySigned) || props.signedIds.includes(event.id)
}
</script>

<style scoped>
.event-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 26px;
}
.event-card {
  display: grid;
  grid-template-columns: 70px 1fr auto;
  gap: 16px;
  align-items: start;
  padding: 16px;
  border: 1px solid color-mix(in srgb, #0D0B08 16%, transparent);
  background: var(--paper);
  transition: background .22s ease, transform .25s var(--ease-gate);
}
.event-card:hover { background: var(--paper-light); transform: translateY(-2px); }
.event-date { display: flex; flex-direction: column; align-items: center; padding-right: 12px; border-right: 1px solid color-mix(in srgb, black 20%, transparent); }
.event-date strong { font-family: var(--font-display); font-size: 36px; font-weight: 400; line-height: 1; }
.event-date span { margin-top: 3px; font-size: 6px; letter-spacing: .06em; color: color-mix(in srgb, black 60%, white); text-align: center; }
.event-main { min-width: 0; }
.event-tag { margin: 0; font-size: 6.5px; letter-spacing: .08em; color: #8A5200; }
.event-main h3 { margin: 5px 0 0; font-family: var(--font-cn); font-size: 20px; font-weight: 900; line-height: 1.1; letter-spacing: -.02em; }
.event-desc { margin: 6px 0 0; font-family: var(--font-cn); font-size: 10px; line-height: 1.65; color: color-mix(in srgb, black 58%, white); }
.event-meta { margin: 8px 0 0; font-size: 6.5px; color: color-mix(in srgb, black 62%, white); }
.event-cap { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.event-bar { flex: 1; height: 3px; background: color-mix(in srgb, black 12%, transparent); }
.event-bar i { display: block; height: 100%; background: var(--acid); }
.event-cap em { font-style: normal; font-size: 6.5px; color: color-mix(in srgb, black 64%, white); }
.event-cta {
  align-self: center;
  padding: 5px 7px;
  border: 1px solid var(--ink);
  background: transparent;
  font-size: 6.5px;
  letter-spacing: .04em;
  transition: background .18s ease, color .18s ease;
}
.event-cta:hover { background: var(--ink); color: var(--acid); }
.event-cta:disabled { opacity: .55; cursor: default; }
.event-cta:disabled:hover { background: transparent; color: inherit; }

@media (max-width: 900px) {
  .event-grid { grid-template-columns: 1fr; gap: 7px; margin-top: 18px; }
  .event-card { grid-template-columns: 52px 1fr auto; gap: 10px; padding: 12px; }
  .event-date strong { font-size: 28px; }
  .event-tag { font-size: 8px; }
  .event-main h3 { font-size: 17px; }
  .event-desc { font-size: 10.5px; }
  .event-meta { margin-top: 5px; font-size: 10px; }
  .event-cap em { font-size: 8px; }
  .event-cta { padding: 6px 8px; font-size: 9px; }
}
</style>
