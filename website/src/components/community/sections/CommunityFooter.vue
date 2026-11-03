<template>
  <section class="cm-foot tone-volt">
    <div class="cm-inner cm-foot-inner">
      <div class="cm-foot-top">
        <p class="cm-foot-kicker mono">NEXT SESSION</p>
        <h2 class="cm-foot-title">下一场：<mark>你</mark></h2>
        <p class="cm-foot-cn">打开小程序约一节课，把名字写进下一张战绩榜。</p>
      </div>

      <div class="cm-foot-grid">
        <div class="cm-foot-col">
          <p class="mono cm-foot-label">CONTACT / 联系</p>
          <p class="cm-foot-line">{{ brand.phone }}</p>
          <p class="cm-foot-line">小程序：VOLT 电压健身</p>
          <p class="mono cm-search">微信搜一搜 · VOLT 电压健身</p>
        </div>
        <div v-for="v in venues" :key="v.code" class="cm-foot-col">
          <p class="mono cm-foot-label">{{ v.code }} / {{ v.en }}</p>
          <p class="cm-foot-line">{{ v.name }}</p>
          <p class="cm-foot-line dim">{{ v.address }}</p>
          <p class="cm-foot-line dim">{{ v.hours }}</p>
        </div>
      </div>

      <div class="cm-foot-actions">
        <button type="button" class="btn btn-ink" @click="$emit('book')">预约下一场</button>
        <button type="button" class="btn cm-foot-ghost" @click="$emit('home')">回到首页 &gt;</button>
      </div>
      <p class="mono cm-foot-bottom">© 2026 VOLT VOLTAGE FITNESS · 广州 天河 / 海珠 / 番禺</p>
    </div>
  </section>
</template>

<script setup>
defineProps({
  brand: { type: Object, required: true },
  venues: { type: Array, required: true },
})
defineEmits(['book', 'home'])
</script>

<style scoped>
.cm-foot { padding: clamp(54px, 8vh, 88px) 0 92px; }
.cm-foot-inner { display: flex; flex-direction: column; }
.cm-foot-kicker { margin: 0; font-size: 8px; letter-spacing: .16em; color: color-mix(in srgb, black 62%, transparent); }
.cm-foot-title {
  margin: 12px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(56px, 8.6vw, 132px);
  font-weight: 900;
  line-height: .9;
  letter-spacing: -.045em;
}
.cm-foot-title mark { display: inline-block; padding: 0 .08em; background: var(--ink); color: var(--acid); }
.cm-foot-cn { margin: 14px 0 0; font-family: var(--font-cn); font-size: 13px; font-weight: 700; letter-spacing: .03em; }

.cm-foot-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr 1fr 1fr;
  gap: 24px;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 2px dashed var(--ink);
}
.cm-foot-label { margin: 0 0 8px; font-size: 7px; letter-spacing: .08em; color: color-mix(in srgb, black 68%, transparent); }
.cm-foot-line { margin: 0; font-family: var(--font-cn); font-size: 11px; font-weight: 700; line-height: 1.7; }
.cm-foot-line.dim { color: color-mix(in srgb, black 66%, transparent); font-size: 9.5px; font-weight: 500; }
.cm-search {
  display: inline-block;
  margin: 8px 0 0;
  padding: 5px 7px;
  border: 1px solid color-mix(in srgb, black 30%, transparent);
  font-size: 8.5px;
  letter-spacing: .04em;
  color: color-mix(in srgb, black 68%, transparent);
}

.cm-foot-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 24px; }
.cm-foot-ghost { border-color: var(--ink); color: var(--ink); }
.cm-foot-ghost:hover { background: var(--ink); color: var(--acid); }
.cm-foot-bottom { margin: 22px 0 0; padding-top: 10px; border-top: 1px solid color-mix(in srgb, black 28%, transparent); font-size: 9px; letter-spacing: .08em; line-height: 1.6; color: color-mix(in srgb, black 70%, transparent); }

@media (max-width: 1100px) {
  .cm-foot-grid { grid-template-columns: 1.3fr 1fr 1fr; }
}

@media (max-width: 900px) {
  .cm-foot { padding: 46px 0 60px; }
  .cm-foot-title { font-size: clamp(52px, 15vw, 78px); }
  .cm-foot-grid { grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 20px; }
  .cm-foot-col:first-child { grid-column: 1 / -1; }
  .cm-foot-actions .btn { flex: 1; }
  .cm-foot-label { font-size: 8px; }
  .cm-foot-line.dim { font-size: 10px; }
  .cm-search { font-size: 8px; }
  .cm-foot-bottom { font-size: 8px; }
}
</style>
