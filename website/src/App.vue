<template>
  <DemoQrPage v-if="view === 'qr'" />
  <StaffPage v-else-if="view === 'staff'" />
  <CommunityPage v-else-if="view === 'community'" />
  <VoltExperience v-else />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import VoltExperience from './components/volt/VoltExperience.vue'
import CommunityPage from './components/community/CommunityPage.vue'
import StaffPage from './components/staff/StaffPage.vue'
import DemoQrPage from './components/demo/DemoQrPage.vue'

const view = ref(readHash())

function readHash() {
  const h = typeof window === 'undefined' ? '' : window.location.hash
  const path = typeof window === 'undefined' ? '' : window.location.pathname
  if (path === '/qr' || path === '/qr/' || path === '/qr.html') return 'qr'
  if (h === '#qr' || h === '#qr/' || h === '#/qr' || h === '#/qr/'
    || h === '#demo-qr' || h === '#demo-qr/') return 'qr'
  if (h === '#staff' || h === '#staff/' || h.startsWith('#staff/')) return 'staff'
  return h === '#community' || h === '#community/' ? 'community' : 'home'
}

function onHash() {
  view.value = readHash()
}

onMounted(() => {
  window.addEventListener('hashchange', onHash)
})
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', onHash)
})
</script>
