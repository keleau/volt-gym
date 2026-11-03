<template>
  <Teleport to="body">
    <div v-if="open" ref="maskEl" class="modal-mask" @click.self="onMask">
      <slot />
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  closeOnMask: { type: Boolean, default: true },
  closeOnEsc: { type: Boolean, default: true },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])
const maskEl = ref(null)

let lastFocused = null
let scrollLocked = []
let active = false

function focusables() {
  const root = maskEl.value
  if (!root) return []
  return Array.from(
    root.querySelectorAll(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
  ).filter((el) => el.getClientRects().length > 0)
}

function lockScroll() {
  const elements = [document.body, ...document.querySelectorAll('[data-page-scroll]')]
  scrollLocked = elements.map((el) => ({ el, overflow: el.style.overflow }))
  elements.forEach((el) => {
    el.style.overflow = 'hidden'
  })
  document.body.dataset.modalOpen = 'true'
}

function unlockScroll() {
  scrollLocked.forEach(({ el, overflow }) => {
    el.style.overflow = overflow
  })
  scrollLocked = []
  delete document.body.dataset.modalOpen
}

function onKeydown(event) {
  if (event.key === 'Escape') {
    if (props.closeOnEsc && !props.busy) {
      event.preventDefault()
      emit('close')
    }
    return
  }
  if (event.key !== 'Tab') return
  const list = focusables()
  if (!list.length) return
  const first = list[0]
  const last = list[list.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function onMask() {
  if (props.closeOnMask && !props.busy) emit('close')
}

function activate() {
  if (active) return
  active = true
  lastFocused = document.activeElement
  lockScroll()
  window.addEventListener('keydown', onKeydown)
  nextTick(() => focusables()[0]?.focus())
}

function deactivate() {
  if (!active) return
  active = false
  window.removeEventListener('keydown', onKeydown)
  unlockScroll()
  lastFocused?.focus?.()
  lastFocused = null
}

watch(
  () => props.open,
  (open) => {
    if (open) activate()
    else deactivate()
  },
  { immediate: true }
)

onBeforeUnmount(deactivate)
</script>
