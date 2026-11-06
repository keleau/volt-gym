<template>
  <div class="qr-page">
    <header class="qr-head">
      <a class="qr-brand" href="#top">VOLT<span>//</span></a>
      <span class="mono qr-head__label">STAFF / 到店核销</span>
      <nav class="qr-links">
        <a class="qr-link mono" href="#staff">店员开卡 →</a>
        <a class="qr-link mono" href="#top">返回官网 →</a>
      </nav>
    </header>

    <main class="qr-shell">
      <p class="qr-kicker mono">SCAN TO CHECK IN / 扫描核销</p>
      <h1>让会员手机，<br /><em>扫这一张。</em></h1>
      <p class="qr-sub">
        小程序「进场」→「开始扫码」，扫描会员当日课程所在门店的二维码。体验课和普通课走同一套核销；体验课不扣次卡。
      </p>

      <section class="qr-grid">
        <article v-for="(venue, index) in venues" :key="venue.code" class="qr-card">
          <div class="qr-card__head">
            <span class="qr-card__index mono">0{{ index + 1 }}</span>
            <span class="qr-card__code mono">{{ venue.code }}</span>
          </div>
          <div class="qr-card__image-wrap">
            <img class="qr-card__image" :src="qrSrc(venue.code)" :alt="`${venue.name} 核销码`" />
          </div>
          <h2>{{ venue.name }}</h2>
          <p class="qr-card__en mono">{{ venue.en }}</p>
          <p class="qr-card__addr">{{ venue.address }}</p>
        </article>
      </section>

      <div class="qr-note">
        <p><b>真机演示：</b>用会员手机扫描电脑屏幕上的二维码；手机和电脑需要能访问同一个后端。</p>
        <p><b>开发者工具：</b>直接点「进场 → 开始扫码」，会自动打开「手动选择场馆」，不需要相机。</p>
        <p class="mono">本地定位不可用时仍按场馆码核销 · 体验课不扣次卡</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { venues } from '../../data/site.js'

function qrSrc(code) {
  return `/images/venue-qr/${code.toLowerCase()}.png`
}
</script>

<style scoped>
.qr-page {
  --qr-muted: color-mix(in srgb, var(--ink) 58%, var(--paper));
  min-height: 100vh;
  background: var(--paper);
  color: var(--ink);
  overflow-x: hidden;
}

.qr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 18px var(--pad);
  border-bottom: 1px solid color-mix(in srgb, var(--ink) 18%, transparent);
}

.qr-brand {
  font-family: var(--font-display);
  font-size: 26px;
  letter-spacing: .04em;
  color: var(--ink);
  text-decoration: none;
}

.qr-brand span {
  display: inline-block;
  margin-left: 1px;
  padding: 0 4px;
  background: var(--acid);
  color: var(--ink);
}

.qr-head__label {
  font-size: 9px;
  letter-spacing: .1em;
  color: var(--qr-muted);
}

.qr-links {
  display: flex;
  align-items: center;
  gap: 18px;
}

.qr-link {
  font-size: 9px;
  letter-spacing: .08em;
  color: var(--qr-muted);
  text-decoration: none;
}

.qr-link:hover {
  color: var(--ink);
}

.qr-shell {
  max-width: 1180px;
  margin: 0 auto;
  padding: clamp(34px, 6vh, 70px) var(--pad) 80px;
}

.qr-kicker {
  font-size: 10px;
  letter-spacing: .12em;
  color: var(--qr-muted);
}

.qr-kicker::before {
  content: "";
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  background: var(--acid);
}

.qr-shell h1 {
  max-width: 760px;
  margin-top: 14px;
  font-family: var(--font-display);
  font-size: clamp(42px, 7vw, 84px);
  line-height: .98;
  letter-spacing: -.045em;
}

.qr-shell h1 em {
  color: var(--acid-deep);
  font-style: normal;
}

.qr-sub {
  max-width: 620px;
  margin-top: 22px;
  font-size: 15px;
  line-height: 1.75;
  color: var(--qr-muted);
}

.qr-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: clamp(30px, 5vh, 54px);
}

.qr-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 18px;
  border: 1px solid color-mix(in srgb, var(--ink) 22%, transparent);
  background: #fffaf0;
  box-shadow: 0 14px 30px color-mix(in srgb, var(--ink) 8%, transparent);
}

.qr-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px dashed color-mix(in srgb, var(--ink) 20%, transparent);
}

.qr-card__index {
  font-size: 10px;
  color: var(--qr-muted);
}

.qr-card__code {
  padding: 4px 7px;
  border: 1px solid color-mix(in srgb, var(--ink) 25%, transparent);
  font-size: 9px;
  letter-spacing: .08em;
}

.qr-card__image-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 18px 0 16px;
  padding: 10px;
  background: #fdf9f0;
}

.qr-card__image {
  width: min(100%, 260px);
  aspect-ratio: 1;
  object-fit: contain;
}

.qr-card h2 {
  font-family: var(--font-display);
  font-size: 28px;
  line-height: 1.1;
}

.qr-card__en {
  margin-top: 3px;
  font-size: 9px;
  letter-spacing: .08em;
  color: var(--qr-muted);
}

.qr-card__addr {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid color-mix(in srgb, var(--ink) 14%, transparent);
  font-size: 12px;
  color: var(--qr-muted);
}

.qr-note {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 26px;
  margin-top: 26px;
  padding-top: 16px;
  border-top: 1px solid color-mix(in srgb, var(--ink) 18%, transparent);
  font-size: 11px;
  line-height: 1.7;
  color: var(--qr-muted);
}

.qr-note p {
  max-width: 520px;
}

.qr-note b {
  color: var(--ink);
}

@media (max-width: 820px) {
  .qr-head__label {
    display: none;
  }

  .qr-grid {
    grid-template-columns: 1fr;
  }

  .qr-card__image {
    width: min(70vw, 260px);
  }
}

@media print {
  .qr-head,
  .qr-kicker,
  .qr-sub,
  .qr-note {
    display: none;
  }

  .qr-shell {
    padding: 0;
  }

  .qr-grid {
    margin-top: 0;
    gap: 8px;
  }

  .qr-card {
    break-inside: avoid;
    box-shadow: none;
  }
}
</style>
