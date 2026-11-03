<template>
  <CommunityPage v-if="view === 'community'" />
  <VoltExperience v-else />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import VoltExperience from './components/volt/VoltExperience.vue'
import CommunityPage from './components/community/CommunityPage.vue'

const view = ref(readHash())

function readHash() {
  const h = typeof window === 'undefined' ? '' : window.location.hash
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
