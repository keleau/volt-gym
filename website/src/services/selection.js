import { reactive, watch } from 'vue'

const KEY = 'volt.selection.v1'
const FIELDS = ['venue', 'program', 'plan']

function clean(value) {
  return Object.fromEntries(
    FIELDS.map((key) => [key, typeof value?.[key] === 'string' ? value[key] : ''])
  )
}

function read() {
  if (typeof localStorage === 'undefined') return clean({})
  try {
    return clean(JSON.parse(localStorage.getItem(KEY) || '{}'))
  } catch {
    return clean({})
  }
}

export const selection = reactive(read())

watch(
  selection,
  (value) => {
    if (typeof localStorage === 'undefined') return
    try {
      localStorage.setItem(KEY, JSON.stringify(clean(value)))
    } catch {
      /* storage may be full or blocked; selection still works in memory */
    }
  },
  { deep: true }
)

export function setSelection(payload = {}) {
  for (const key of FIELDS) {
    if (typeof payload[key] === 'string') selection[key] = payload[key]
  }
}

export function clearSelection() {
  for (const key of FIELDS) selection[key] = ''
}
