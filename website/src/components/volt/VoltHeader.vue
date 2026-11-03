<template>
  <header class="volt-header">
    <div class="bar">
      <a class="brand" href="#top" aria-label="VOLT 电压健身首页" @click.prevent="go(0)">
        VOLT<span class="slash">//</span>
      </a>

      <nav class="nav" aria-label="主导航">
        <button
          v-for="(item, i) in nav"
          :key="item.id"
          type="button"
          class="nav-item"
          :class="{ active: isActive(item) }"
          :aria-current="isActive(item) ? 'step' : undefined"
          @click="pick(item)"
        >
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-en">{{ item.en }}</span>
        </button>
      </nav>

      <div class="actions">
        <button
          type="button"
          class="header-cta"
          @click="go(cta ?? steps.length - 1, { plan: 'PLAN-01' })"
        >
          <span class="price">¥{{ brand.trialPrice }}</span>
          <span>立即预约</span>
        </button>
        <button
          type="button"
          class="burger"
          :aria-expanded="menuOpen"
          aria-label="打开页面目录"
          @click="menuOpen = !menuOpen"
        >
          <span></span><span></span>
        </button>
      </div>
    </div>

    <Transition name="drawer">
      <div v-if="menuOpen" class="drawer">
        <button
          v-for="(item, i) in nav"
          :key="item.id"
          type="button"
          class="drawer-item"
          :class="{ active: isActive(item) }"
          @click="pick(item)"
        >
          <span class="idx">{{ item.no }}</span>
          <span class="label">{{ item.label }}</span>
          <span class="en">{{ item.en }}</span>
        </button>
        <button
          type="button"
          class="btn btn-acid drawer-cta"
          @click="go(cta ?? steps.length - 1, { plan: 'PLAN-01' })"
        >
          ¥{{ brand.trialPrice }} 立即预约
        </button>
      </div>
    </Transition>
  </header>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { brand } from '../../data/site.js'

const props = defineProps({
  steps: { type: Array, required: true },
  nav: { type: Array, default: () => [] },
  active: { type: Number, default: 0 },
  activePage: { type: String, default: '' },
  cta: { type: Number, default: null },
})
const emit = defineEmits(['go', 'community'])
const menuOpen = ref(false)

function go(index, payload) {
  menuOpen.value = false
  emit('go', index, payload)
}
function isActive(item) {
  if (item.page) return props.activePage === 'community'
  return props.active === item.i
}
function pick(item) {
  if (item.page) {
    menuOpen.value = false
    emit('community')
    return
  }
  go(item.i)
}

function onKeydown(event) {
  if (event.key === 'Escape') menuOpen.value = false
}

watch(
  () => props.active,
  () => {
    menuOpen.value = false
  }
)

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.volt-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: 120;
  background: var(--ink);
  border-bottom: 1px solid transparent;
  color: var(--paper);
}

.bar {
  width: 100%;
  max-width: var(--maxw);
  height: var(--header-h);
  margin: 0 auto;
  padding: 0 var(--pad);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  flex: none;
  font-family: var(--font-display);
  font-size: 30px;
  line-height: 1;
  letter-spacing: .04em;
}
.brand .slash {
  display: inline-block;
  margin-left: 1px;
  padding: 0 4px;
  background: var(--acid);
  color: var(--ink);
}

.nav {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 0 13px;
  color: var(--paper);
  text-align: left;
  transition: color .2s ease, background .2s ease;
}
.nav-item:hover { color: var(--acid); background: color-mix(in srgb, whitesmoke 5%, transparent); }
.nav-item.active { color: var(--acid); }

.nav-en { display: none; }

.nav-label {
  font-family: var(--font-cn);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: .02em;
}

.actions { display: flex; align-items: center; gap: 12px; flex: none; }

.header-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 8px 16px;
  background: var(--acid);
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: .06em;
  transition: background .2s ease, color .2s ease;
}
.header-cta:hover { background: var(--paper-light); }
.header-cta .price { font-family: var(--font-display); font-size: 16px; letter-spacing: .03em; }

.burger { display: none; width: 34px; height: 34px; padding: 8px 4px; }
.burger span {
  display: block;
  width: 24px;
  height: 2px;
  margin: 5px 0;
  background: currentColor;
  transition: transform .2s ease, opacity .2s ease;
}

.drawer {
  display: none;
  position: absolute;
  top: var(--header-h);
  left: 0;
  right: 0;
  padding: 10px var(--pad) 22px;
  background: var(--ink);
  border-top: 1px solid var(--line-paper);
  border-bottom: 1px solid var(--acid);
}

.drawer-item {
  width: 100%;
  display: grid;
  grid-template-columns: 34px 1fr auto;
  align-items: baseline;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid color-mix(in srgb, whitesmoke 12%, transparent);
  text-align: left;
  color: var(--paper);
}
.drawer-item.active { color: var(--acid); }
.drawer-item .idx { font-family: var(--font-mono); font-size: 10px; opacity: .65; }
.drawer-item .label { font-family: var(--font-cn); font-size: 22px; font-weight: 900; }
.drawer-item .en { font-family: var(--font-mono); font-size: 9px; letter-spacing: .1em; opacity: .5; }
.drawer-cta { width: 100%; margin-top: 18px; }

.drawer-enter-active,
.drawer-leave-active { transition: opacity .18s ease, transform .18s ease; }
.drawer-enter-from,
.drawer-leave-to { opacity: 0; transform: translateY(-8px); }

@media (max-width: 1000px) {
  .nav-item { padding: 0 9px; }
  .nav-en { display: none; }
}

@media (max-width: 900px) {
  .nav,
  .header-cta { display: none; }
  .burger { display: block; }
  .drawer { display: block; }
  .brand { font-size: 26px; }
}
</style>
