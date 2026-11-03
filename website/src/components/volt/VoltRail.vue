<template>
  <aside class="rail" aria-label="页面进度">
    <div class="tracks" aria-hidden="true">
      <i v-for="n in steps.length" :key="n" :style="{ '--i': n }"></i>
      <span class="fill" :style="{ '--fill': fill }"></span>
    </div>

    <div class="nodes">
      <button
        v-for="(step, i) in steps"
        :key="step.id"
        type="button"
        class="node"
        :class="{ active: active === i, passed: i < active }"
        :aria-label="`跳到第 ${step.no} 屏：${step.label}`"
        :aria-current="active === i ? 'step' : undefined"
        @click="$emit('go', i)"
      >
        <span class="node-no">{{ step.no }}</span>
        <span class="node-label">{{ step.label }}<i>{{ step.en }}</i></span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  steps: { type: Array, required: true },
  active: { type: Number, default: 0 },
})
defineEmits(['go'])

const fill = computed(() => {
  if (props.steps.length < 2) return '0%'
  return `${(props.active / (props.steps.length - 1)) * 100}%`
})
</script>

<style scoped>
.rail {
  position: fixed;
  top: 50%;
  right: max(14px, calc((100vw - var(--maxw)) / 2 - 38px));
  z-index: 115;
  height: 300px;
  width: 22px;
  transform: translateY(-50%);
}

.tracks {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 4px;
  width: 12px;
}

.tracks i {
  position: absolute;
  top: 0;
  bottom: 0;
  left: calc((var(--i) - 1) * 2px);
  width: 1px;
  background: repeating-linear-gradient(
    to bottom,
    color-mix(in srgb, orange 28%, transparent) 0 3px,
    transparent 3px 9px
  );
  opacity: .62;
  animation: track-flow 10.9s linear infinite;
}
.tracks i:nth-child(even) { animation-direction: reverse; opacity: .36; }

.fill {
  position: absolute;
  top: 0;
  left: 5px;
  width: 2px;
  height: var(--fill);
  background: var(--acid);
  box-shadow: 0 0 10px color-mix(in srgb, orange 65%, transparent);
  transition: height .5s var(--ease-gate);
}

@keyframes track-flow {
  to { background-position-y: 90px; }
}

.nodes {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
}

.node {
  position: relative;
  display: flex;
  align-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
}

.node-no {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, whitesmoke 22%, transparent);
  border-radius: 50%;
  background: var(--ink);
  color: color-mix(in srgb, oldlace 58%, transparent);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: -.02em;
  transition: background .25s ease, color .25s ease, border-color .25s ease;
}

.node.passed .node-no { border-color: color-mix(in srgb, orange 55%, transparent); color: var(--acid-soft); }

.node.active .node-no {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
  box-shadow: 0 0 0 4px color-mix(in srgb, black 70%, transparent), 0 0 18px color-mix(in srgb, orange 45%, transparent);
}

.node-label {
  position: absolute;
  right: 30px;
  top: 50%;
  display: flex;
  align-items: baseline;
  gap: 7px;
  padding: 3px 7px;
  background: var(--panel-2);
  color: var(--paper);
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  opacity: 0;
  transform: translate(6px, -50%);
  pointer-events: none;
  transition: opacity .2s ease, transform .2s ease;
}
.node-label i {
  font-family: var(--font-mono);
  font-size: 8px;
  font-style: normal;
  font-weight: 400;
  letter-spacing: .08em;
  color: color-mix(in srgb, oldlace 62%, transparent);
}
.node:hover .node-label,
.node:focus-visible .node-label {
  opacity: 1;
  transform: translate(0, -50%);
}

@media (max-width: 1100px) {
  .rail { display: none; }
}
@media (max-height: 720px) and (min-width: 1101px) {
  .rail { height: 230px; }
}
</style>
