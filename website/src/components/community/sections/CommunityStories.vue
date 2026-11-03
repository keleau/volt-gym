<template>
  <section class="cm-stories tone-ink">
    <div class="cm-inner">
      <div class="cm-section-head" data-reveal>
        <div>
          <p class="cm-no mono">03 / STORIES</p>
          <h2>为什么留下来</h2>
        </div>
        <p class="cm-note">不办年卡，也没有推销。留下来的理由，只能是一次次真的练到。</p>
      </div>

      <div class="story-grid">
        <article v-for="(story, i) in stories" :key="story.name" class="story-card" data-reveal>
          <figure class="story-photo">
            <img :src="story.img" :alt="story.name" loading="lazy" decoding="async" />
            <span class="story-index mono">0{{ i + 1 }}</span>
          </figure>
          <div class="story-body">
            <p class="mono story-tag">COACH STORY / {{ story.tag }}</p>
            <blockquote>“{{ story.quote }}”</blockquote>
            <div class="story-person">
              <strong>{{ story.name }}</strong>
              <span>{{ story.role }}</span>
            </div>
            <dl class="story-stats mono">
              <div><dt>SESSIONS</dt><dd>{{ story.sessions }}</dd></div>
              <div><dt>STREAK</dt><dd>{{ story.streak }}</dd></div>
              <div><dt>FAVORITE</dt><dd>{{ story.favorite }}</dd></div>
            </dl>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  stories: { type: Array, required: true },
})
</script>

<style scoped>
.cm-stories .cm-section-head { border-bottom-color: var(--line-paper); }
.cm-stories .cm-no { color: var(--acid); }
.cm-stories .cm-note { color: color-mix(in srgb, oldlace 64%, transparent); }

.story-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: clamp(14px, 2.4vw, 28px);
  margin-top: 28px;
}
.story-card {
  display: grid;
  grid-template-columns: minmax(150px, .72fr) minmax(0, 1.28fr);
  border: 1px solid var(--border);
  background: var(--panel);
  min-height: 280px;
}
.story-photo { position: relative; margin: 0; overflow: hidden; min-height: 250px; }
.story-photo img { width: 100%; height: 100%; object-fit: cover; object-position: center 16%; filter: grayscale(.4) contrast(1.08) brightness(.82); transition: filter .4s ease, transform .7s var(--ease-gate); }
.story-card:hover .story-photo img { filter: grayscale(0) brightness(.92); transform: scale(1.03); }
.story-index {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 6px;
  background: var(--acid);
  color: var(--ink);
  font-size: 7px;
}
.story-body { display: flex; flex-direction: column; padding: 18px 20px 16px; }
.story-tag { margin: 0; font-size: 6.5px; letter-spacing: .1em; color: var(--acid); }
.story-body blockquote {
  margin: 14px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(16px, 1.5vw, 20px);
  font-weight: 900;
  line-height: 1.55;
  letter-spacing: -.01em;
}
.story-person { display: flex; align-items: baseline; gap: 8px; margin-top: 14px; }
.story-person strong { font-family: var(--font-cn); font-size: 16px; font-weight: 900; }
.story-person span { font-family: var(--font-cn); font-size: 9px; color: color-mix(in srgb, oldlace 62%, transparent); }
.story-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin: auto 0 0;
  padding-top: 14px;
  border-top: 1px solid color-mix(in srgb, oldlace 12%, transparent);
}
.story-stats dt { font-size: 6px; letter-spacing: .06em; color: color-mix(in srgb, oldlace 58%, transparent); }
.story-stats dd { margin: 3px 0 0; font-size: 8px; color: color-mix(in srgb, oldlace 62%, transparent); white-space: nowrap; }

@media (max-width: 1100px) {
  .story-card { grid-template-columns: .8fr 1.2fr; }
}

@media (max-width: 900px) {
  .story-grid { grid-template-columns: 1fr; gap: 10px; margin-top: 18px; }
  .story-card { grid-template-columns: .72fr 1.28fr; min-height: 220px; }
  .story-photo { min-height: 200px; }
  .story-body { padding: 13px 14px 12px; }
  .story-tag { font-size: 8px; }
  .story-body blockquote { margin-top: 8px; font-size: 15px; line-height: 1.5; }
  .story-person { margin-top: 10px; }
  .story-stats { padding-top: 9px; }
  .story-stats dt { font-size: 7px; }
  .story-stats dd { font-size: 9.5px; }
}
</style>
