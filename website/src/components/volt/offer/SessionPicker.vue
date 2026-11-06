<template>
  <div class="session-picker">
    <div class="session-head">
      <span class="session-label"><span class="mono">06</span>场次</span>
      <span class="session-hint mono">{{ hint }}</span>
    </div>

    <div v-if="sessions.length" class="session-list" role="radiogroup" aria-label="选择场次">
      <button
        v-for="session in sessions"
        :key="session.id"
        type="button"
        class="session-chip"
        :class="{ selected: selected === session.id, 'is-full': !isOpen(session) }"
        :aria-pressed="selected === session.id"
        :disabled="!isOpen(session)"
        @click="$emit('select', session)"
      >
        <strong>{{ session.time || session.startTime }}</strong>
        <span>
          {{ isOpen(session) ? `余 ${session.left} 位` : '已满' }}
          <template v-if="session.coachName"> · {{ session.coachName }}</template>
        </span>
      </button>
    </div>

    <p v-else class="session-empty mono">{{ hint }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  selected: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  loaded: { type: Boolean, default: false },
})
defineEmits(['select'])

function isOpen(session) {
  return !session.soldOut && Number(session.left) > 0
}

const hint = computed(() => {
  if (props.loading) return '正在查场次…'
  if (!props.loaded) return '选好门店和日期后显示时段'
  if (!props.sessions.length) return '当天暂无可约场次'
  return '选一个时间'
})
</script>

<style scoped>
.session-picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.session-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-cn);
  font-size: 11px;
  font-weight: 700;
  color: color-mix(in srgb, #0D0B08 76%, transparent);
}
.session-label .mono {
  font-size: 7.5px;
  letter-spacing: .06em;
  color: #8A5200;
}
.session-hint {
  font-size: 7.5px;
  letter-spacing: .06em;
  color: color-mix(in srgb, #0D0B08 54%, transparent);
}
.session-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.session-chip {
  flex: 1 1 120px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid color-mix(in srgb, #0D0B08 22%, transparent);
  border-radius: 0;
  background: #ffffff;
  color: var(--ink);
  text-align: left;
  transition: border-color .18s ease, background .18s ease, box-shadow .18s ease;
}
.session-chip strong {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
}
.session-chip span {
  max-width: 100%;
  overflow: hidden;
  font-family: var(--font-cn);
  font-size: 9px;
  line-height: 1.4;
  color: color-mix(in srgb, #0D0B08 58%, transparent);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-chip:hover:not(:disabled) { border-color: var(--acid); }
.session-chip.selected {
  border-color: var(--acid);
  background: color-mix(in srgb, #F59E0B 13%, white);
  box-shadow: inset 0 0 0 1px var(--acid);
}
.session-chip.selected strong { color: #8A5200; }
.session-chip.is-full {
  opacity: .5;
  cursor: default;
  background: color-mix(in srgb, black 4%, white);
}
.session-empty {
  margin: 0;
  font-size: 8px;
  letter-spacing: .02em;
  color: color-mix(in srgb, #0D0B08 54%, transparent);
}

@media (max-width: 900px) {
  .session-chip { flex: 1 1 100px; }
  .session-label { font-size: 10.5px; }
}
</style>
