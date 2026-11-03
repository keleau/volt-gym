import { onBeforeUnmount, ref } from 'vue'

export function useCooldown({ intervalMs = 250 } = {}) {
  const remaining = ref(0)
  let timer = 0
  let until = 0
  let onDone = null

  function stop() {
    clearInterval(timer)
    timer = 0
    until = 0
    remaining.value = 0
  }

  function tick() {
    remaining.value = Math.max(0, Math.ceil((until - Date.now()) / 1000))
    if (remaining.value === 0) {
      stop()
      onDone?.()
      onDone = null
    }
  }

  function start(seconds, done) {
    const value = Number(seconds) || 0
    if (value <= 0) return
    onDone = done
    until = Date.now() + value * 1000
    remaining.value = value
    clearInterval(timer)
    timer = setInterval(tick, intervalMs)
  }

  onBeforeUnmount(stop)

  return { remaining, start, stop }
}
