<template>
  <section class="cm-venues tone-ink">
    <div class="cm-inner">
      <div class="cm-section-head" data-reveal>
        <div>
          <p class="cm-no mono">05 / STATIONS</p>
          <h2>门店现场</h2>
        </div>
        <p class="cm-note">三处充电站，同一套训练语言。选最近的，进来练。</p>
      </div>

      <div class="venue-gallery">
        <article
          v-for="venue in venues"
          :key="venue.code"
          class="venue-card"
          role="button"
          tabindex="0"
          :aria-label="`${venue.name}，去预约`"
          data-reveal
          @click="$emit('venue', venue.code)"
          @keydown.enter.prevent="$emit('venue', venue.code)"
          @keydown.space.prevent="$emit('venue', venue.code)"
        >
          <img :src="venue.img" :alt="venue.name" loading="lazy" decoding="async" />
          <div class="venue-overlay">
            <span class="mono venue-code">{{ venue.code }} / {{ venue.en }}</span>
            <h3>{{ venue.name }}</h3>
            <p class="mono">{{ venue.address }} · {{ venue.hours }}</p>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  venues: { type: Array, required: true },
})
defineEmits(['venue'])
</script>

<style scoped>
.cm-venues .cm-section-head { border-bottom-color: var(--line-paper); }
.cm-venues .cm-no { color: var(--acid); }
.cm-venues .cm-note { color: color-mix(in srgb, oldlace 64%, transparent); }

.venue-gallery {
  display: grid;
  grid-template-columns: 1.2fr .9fr .9fr;
  gap: 10px;
  margin-top: 26px;
}
.venue-card {
  position: relative;
  height: 260px;
  overflow: hidden;
  background: var(--ink);
  cursor: pointer;
}
.venue-card:focus-visible { outline: 2px solid var(--acid); outline-offset: 3px; }
.venue-card:first-child { height: 320px; margin-top: -30px; }
.venue-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: grayscale(.45) contrast(1.08) brightness(.76);
  transition: filter .4s ease, transform .7s var(--ease-gate);
}
.venue-card:hover img { filter: grayscale(0) brightness(.94); transform: scale(1.04); }
.venue-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px;
  background: linear-gradient(0deg, rgba(13, 11, 8, .88), transparent);
}
.venue-code { font-size: 6.5px; letter-spacing: .1em; color: var(--acid); }
.venue-overlay h3 { margin: 5px 0 0; font-family: var(--font-cn); font-size: 24px; font-weight: 900; line-height: 1; }
.venue-overlay p { margin: 6px 0 0; font-size: 6.5px; color: color-mix(in srgb, oldlace 64%, transparent); }

@media (max-width: 900px) {
  .venue-gallery { grid-template-columns: 1fr 1fr; gap: 7px; margin-top: 18px; }
  .venue-card, .venue-card:first-child { height: 180px; margin-top: 0; }
  .venue-card:first-child { grid-column: 1 / -1; height: 220px; }
  .venue-overlay { padding: 11px; }
  .venue-overlay h3 { font-size: 20px; }
  .venue-overlay p { font-size: 8px; }
}
</style>
