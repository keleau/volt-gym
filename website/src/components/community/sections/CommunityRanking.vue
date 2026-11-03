<template>
  <section class="cm-rank tone-paper">
    <div class="cm-inner">
      <div class="cm-section-head" data-reveal>
        <div>
          <p class="cm-no mono">02 / RANKING</p>
          <h2>本周热练榜</h2>
        </div>
        <p class="cm-note">过去 30 天 · 三店共同冲榜 · 每周一 0 点更新</p>
      </div>

      <div class="rank-grid">
        <div class="cr-table" role="table" aria-label="社区战绩榜" data-reveal>
          <div class="cr-row cr-row-head mono" role="row">
            <span>#</span><span>代号</span><span>训练</span><span>时长</span><span>连续</span>
          </div>
          <div
            v-for="(p, i) in podium"
            :key="p.code"
            class="cr-row"
            :class="[`cr-p-${i + 1}`, { 'cr-me': p.me }]"
            role="row"
          >
            <span class="cr-idx">{{ p.no }}</span>
            <span class="cr-code">{{ p.code }}<i v-if="p.me" class="mono">ME</i></span>
            <span class="mono cr-num">{{ p.sessions }} 次</span>
            <span class="mono cr-num">{{ p.minutes }} 分</span>
            <span class="mono cr-num">{{ p.weeks }} 周</span>
          </div>
          <p class="cr-tip mono">训练每满 1 小时计 1 点 · 榜首可在门店前台领 VOLT 贴纸</p>
        </div>

        <aside class="challenge" data-reveal>
          <p class="mono challenge-kicker">WEEKLY CHALLENGE / 本周挑战</p>
          <div
            class="challenge-ring"
            :style="{ '--pct': `${challenge.percent}%` }"
            :aria-label="`本周挑战完成度 ${challenge.percent}%`"
          >
            <div><strong>{{ challenge.percent }}</strong><span>%</span></div>
          </div>
          <h3>{{ challenge.title }}</h3>
          <p class="challenge-desc">{{ challenge.desc }}</p>
          <div class="challenge-meta mono">
            <span><b>{{ challenge.joined }}</b> JOINED</span>
            <span><b>0{{ challenge.sessions }}</b> SESSIONS</span>
            <span><b>0{{ challenge.days }}</b> DAYS</span>
          </div>
          <button
            type="button"
            class="btn btn-ink challenge-btn"
            :disabled="joined"
            @click="$emit('join')"
          >
            {{ joined ? '已加入挑战' : '加入挑战 >' }}
          </button>
        </aside>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  podium: { type: Array, required: true },
  challenge: { type: Object, required: true },
  joined: { type: Boolean, default: false },
})
defineEmits(['join'])
</script>

<style scoped>
.rank-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.38fr) minmax(280px, .62fr);
  gap: clamp(18px, 3vw, 34px);
  margin-top: 28px;
}
.cr-table {
  align-self: start;
  border: 1px solid color-mix(in srgb, #0D0B08 16%, transparent);
  background: var(--paper);
}
.cr-row {
  display: grid;
  grid-template-columns: 48px 1.4fr .8fr .8fr .8fr;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid color-mix(in srgb, black 14%, transparent);
}
.cr-row-head {
  font-size: 7px;
  letter-spacing: .1em;
  color: color-mix(in srgb, black 62%, white);
  background: color-mix(in srgb, black 4%, transparent);
}
.cr-row:last-of-type { border-bottom: none; }
.cr-idx { font-family: var(--font-display); font-size: 22px; line-height: 1; }
.cr-p-1 .cr-idx { color: #8A3B00; }
.cr-p-2 .cr-idx { color: #7A4700; }
.cr-p-3 .cr-idx { color: #6B4200; }
.cr-code { font-family: var(--font-mono); font-size: 12px; letter-spacing: .04em; }
.cr-code i {
  margin-left: 7px;
  padding: 2px 5px;
  border: 1px solid var(--ink);
  font-size: 6px;
  font-style: normal;
  vertical-align: middle;
}
.cr-num { font-size: 10px; text-align: right; color: color-mix(in srgb, black 64%, white); }
.cr-me { background: color-mix(in srgb, orange 9%, transparent); }
.cr-tip {
  margin: 0;
  padding: 12px 16px;
  border-top: 1px solid color-mix(in srgb, black 14%, transparent);
  max-width: 30em;
  font-size: 13px;
  line-height: 1.65;
  letter-spacing: .04em;
  color: color-mix(in srgb, black 64%, white);
}

.challenge {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 22px 18px;
  background: var(--panel);
  color: var(--paper);
  text-align: center;
}
.challenge-kicker { margin: 0; font-size: 6.5px; letter-spacing: .1em; color: var(--acid); }
.challenge-ring {
  width: 132px;
  height: 132px;
  margin-top: 16px;
  border-radius: 50%;
  background: conic-gradient(
    var(--acid) 0 var(--pct, 68%),
    color-mix(in srgb, oldlace 14%, transparent) var(--pct, 68%) 100%
  );
  display: grid;
  place-items: center;
}
.challenge-ring div {
  width: 106px;
  height: 106px;
  border-radius: 50%;
  background: var(--panel);
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: 28px;
}
.challenge-ring strong { font-family: var(--font-display); font-size: 42px; font-weight: 400; line-height: 1; color: var(--paper); }
.challenge-ring span { font-family: var(--font-mono); font-size: 11px; color: var(--acid); }
.challenge h3 { margin: 14px 0 0; font-family: var(--font-cn); font-size: 20px; font-weight: 900; }
.challenge-desc { margin: 7px 0 0; font-family: var(--font-cn); font-size: 10px; line-height: 1.7; color: color-mix(in srgb, oldlace 54%, transparent); }
.challenge-meta {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid color-mix(in srgb, oldlace 14%, transparent);
  font-size: 6px;
  letter-spacing: .06em;
  color: color-mix(in srgb, oldlace 60%, transparent);
}
.challenge-meta b { color: var(--acid); font-weight: 500; }
.challenge-btn { margin-top: 14px; width: 100%; min-height: 36px; font-size: 9px; }
.challenge-btn:disabled { opacity: .62; cursor: default; }
.challenge-btn:disabled:hover { background: var(--ink); border-color: var(--ink); color: var(--paper-light); transform: none; }

@media (max-width: 1100px) {
  .rank-grid { grid-template-columns: 1.3fr .7fr; }
}

@media (max-width: 900px) {
  .rank-grid { display: block; margin-top: 18px; }
  .cr-row { grid-template-columns: 36px 1.4fr .8fr .8fr; gap: 6px; padding: 10px 11px; }
  .cr-row-head span:nth-child(5), .cr-row > span:nth-child(5) { display: none; }
  .cr-idx { font-size: 18px; }
  .cr-code { font-size: 11px; }
  .cr-num { font-size: 10.5px; }
  .cr-tip { font-size: 12px; padding: 10px 12px; }
  .cr-row-head { font-size: 8px; }
  .challenge { margin-top: 12px; padding: 16px; }
  .challenge-ring { width: 104px; height: 104px; margin-top: 10px; }
  .challenge-ring div { width: 82px; height: 82px; padding-top: 21px; }
  .challenge-ring strong { font-size: 32px; }
  .challenge h3 { font-size: 18px; }
  .challenge-desc { font-size: 11px; }
  .challenge-meta { font-size: 8px; }
  .challenge-btn { margin-top: 10px; }
}
</style>
