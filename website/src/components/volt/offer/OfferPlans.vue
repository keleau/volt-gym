<template>
  <section class="offer-price" :class="{ 'is-on': active }">
    <header class="price-head">
      <p class="offer-no mono">04 / PRICE + BOOKING</p>
      <h2>今夜<em>电价</em></h2>
      <p class="price-note">先约一节体验，或者直接把 10 次卡记到手机号上。</p>
    </header>

    <div class="plan-list" role="radiogroup" aria-label="选择价格">
      <button
        v-for="(plan, i) in plans"
        :key="plan.code"
        type="button"
        class="plan-row"
        :class="{ selected: selected === plan.code }"
        :aria-pressed="selected === plan.code"
        :style="{ '--i': i }"
        @click="$emit('select', plan)"
      >
        <span class="plan-code mono">{{ plan.code }}</span>
        <span class="plan-main">
          <span class="plan-price">
            <small>¥</small><b>{{ plan.price }}</b><i>{{ plan.unit }}</i>
          </span>
          <span class="plan-name">{{ plan.name }}</span>
          <span class="plan-line">{{ planLines[plan.code] }}</span>
        </span>
        <span class="plan-arrow" aria-hidden="true">→</span>
      </button>
    </div>

    <footer class="price-foot mono">
      <span>体验课到店付款 · 次卡开卡后小程序约课</span>
      <span>单次到店 ¥99</span>
    </footer>
  </section>
</template>

<script setup>
defineProps({
  plans: { type: Array, required: true },
  selected: { type: String, default: '' },
  active: { type: Boolean, default: false },
})
defineEmits(['select'])

const planLines = {
  'PLAN-01': '团课 1 节 + InBody 体测 + 教练建议',
  'PLAN-03': '10 节 / 180 天，到店付款后开卡',
}
</script>

<style scoped>
.offer-price {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding-top: clamp(2px, 1vh, 10px);
}

.price-head {
  flex: none;
  padding-bottom: 14px;
  border-bottom: 1px solid color-mix(in srgb, oldlace 16%, transparent);
}
.offer-no {
  margin: 0;
  font-size: 10px;
  letter-spacing: .14em;
  color: color-mix(in srgb, orange 82%, transparent);
}
.price-head h2 {
  margin: 7px 0 0;
  font-family: var(--font-cn);
  font-size: clamp(52px, 5.2vw, 82px);
  font-weight: 900;
  line-height: .94;
  letter-spacing: -.06em;
}
.price-head h2 em { font-style: normal; color: var(--acid); }
.price-note {
  max-width: 340px;
  margin: 14px 0 0;
  font-family: var(--font-cn);
  font-size: 15px;
  line-height: 1.7;
  color: color-mix(in srgb, #F2EFE4 62%, transparent);
}

.plan-list {
  flex: none;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 8px;
  margin-top: clamp(18px, 4vh, 40px);
  padding: 0;
}
.plan-row {
  flex: none;
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 14px;
  min-height: 92px;
  padding: 12px 15px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--paper);
  text-align: left;
  opacity: 0;
  transform: translateY(12px);
  transition:
    opacity .4s ease calc(var(--i) * 70ms + .12s),
    transform .5s var(--ease-gate) calc(var(--i) * 70ms + .12s),
    background .2s ease, border-color .2s ease, color .2s ease;
}
.offer-price.is-on .plan-row { opacity: 1; transform: none; }
.plan-row:hover,
.plan-row:focus-visible {
  border-color: var(--acid);
  outline: none;
}
.plan-row.selected {
  background: color-mix(in srgb, #F59E0B 15%, #1A1610);
  border-color: var(--acid);
  color: var(--paper);
}

.plan-code {
  align-self: start;
  padding-top: 5px;
  font-size: 8px;
  letter-spacing: .08em;
  color: color-mix(in srgb, orange 82%, transparent);
}
.plan-row.selected .plan-code { color: var(--acid); }

.plan-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-areas:
    'price name'
    'price line';
  align-items: center;
  column-gap: 15px;
  row-gap: 2px;
  min-width: 0;
}
.plan-price {
  grid-area: price;
  display: flex;
  align-items: baseline;
  gap: 2px;
  white-space: nowrap;
}
.plan-price small {
  font-family: var(--font-display);
  font-size: 17px;
  color: var(--acid);
}
.plan-price b {
  font-family: var(--font-display);
  font-size: clamp(34px, 3.4vw, 46px);
  font-weight: 400;
  line-height: .82;
}
.plan-price i {
  margin-left: 5px;
  font-family: var(--font-mono);
  font-size: 8px;
  font-style: normal;
  color: color-mix(in srgb, oldlace 52%, transparent);
}
.plan-row.selected .plan-price small,
.plan-row.selected .plan-price i { color: var(--ash); }

.plan-name {
  grid-area: name;
  font-family: var(--font-cn);
  font-size: 16px;
  font-weight: 900;
  line-height: 1.2;
  letter-spacing: -.02em;
}
.plan-line {
  grid-area: line;
  font-family: var(--font-cn);
  font-size: 9.5px;
  line-height: 1.45;
  color: color-mix(in srgb, var(--paper) 64%, transparent);
}
.plan-row.selected .plan-line { color: var(--ash); }

.plan-arrow {
  justify-self: end;
  font-size: 18px;
  line-height: 1;
  color: color-mix(in srgb, oldlace 50%, transparent);
  transition: transform .18s ease, color .18s ease;
}
.plan-row.selected .plan-arrow { color: var(--acid); }
.plan-row:hover .plan-arrow { transform: translateX(3px); color: var(--acid); }
.plan-row.selected:hover .plan-arrow { color: var(--acid); }

.price-foot {
  flex: none;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid color-mix(in srgb, oldlace 14%, transparent);
  font-size: 8px;
  letter-spacing: .1em;
  color: color-mix(in srgb, oldlace 52%, transparent);
}

@media (max-width: 1100px) and (min-width: 901px) {
  .price-head h2 { font-size: clamp(46px, 5vw, 66px); }
  .plan-row { grid-template-columns: 46px minmax(0, 1fr) 20px; gap: 10px; padding: 10px 12px; }
  .plan-price b { font-size: 34px; }
}

@media (max-height: 800px) and (min-width: 901px) {
  .price-head h2 { font-size: clamp(44px, 4.6vw, 68px); }
  .price-note { margin-top: 10px; }
  .plan-row { min-height: 78px; padding: 9px 12px; }
  .plan-price b { font-size: 34px; }
  .plan-name { font-size: 15px; }
}

@media (max-width: 900px) {
  .offer-price { flex: none; }
  .price-head { padding-bottom: 10px; }
  .price-head h2 { font-size: clamp(44px, 12vw, 56px); }
  .price-note { margin-top: 8px; font-size: 11px; }
  .plan-list {
    flex: none;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
    margin-top: 0;
    padding: 10px 0 0;
    justify-content: stretch;
  }
  .plan-row {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    min-height: 112px;
    gap: 0;
    padding: 8px;
  }
  .plan-code { align-self: flex-start; padding-top: 0; font-size: 7px; }
  .plan-main { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; width: 100%; }
  .plan-price { display: flex; margin-top: 7px; }
  .plan-price b { font-size: 30px; }
  .plan-price small { font-size: 13px; }
  .plan-price i { display: none; }
  .plan-name { font-size: 12px; margin-top: 4px; }
  .plan-line { display: none; }
  .plan-arrow { display: none; }
  .price-foot { margin-top: 10px; padding-top: 9px; font-size: 7px; }
}
</style>
