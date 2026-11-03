<template>
  <div ref="pageEl" class="community-page" data-page-scroll>
    <VoltHeader
      :steps="homeSteps"
      :nav="navMain"
      :active="0"
      active-page="community"
      :cta="3"
      @go="handleGo"
      @community="scrollTop"
    />

    <main class="cm-main">
      <CommunityHero :stats="stats" @join="goBooking(true)" @events="scrollToEvents" />
      <CommunityRanking
        :podium="podium"
        :challenge="challenge"
        :joined="challengeJoined"
        @join="openChallenge"
      />
      <CommunityStories :stories="stories" />
      <CommunityEvents
        ref="eventsEl"
        :events="events"
        :signed-ids="signedEventIds"
        @open="openEvent"
      />
      <CommunityVenues :venues="venueCards" @venue="goVenue" />
      <CommunityFooter :brand="brand" :venues="venues" @book="goBooking()" @home="goHome" />
    </main>

    <CommunitySignupDialog
      :open="dialog.open"
      :kind="dialog.kind"
      :event="dialog.event"
      :challenge="challenge"
      @close="closeDialog"
      @joined="onJoined"
      @refresh="refreshCommunity"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import VoltHeader from '../volt/VoltHeader.vue'
import CommunityHero from './sections/CommunityHero.vue'
import CommunityRanking from './sections/CommunityRanking.vue'
import CommunityStories from './sections/CommunityStories.vue'
import CommunityEvents from './sections/CommunityEvents.vue'
import CommunityVenues from './sections/CommunityVenues.vue'
import CommunityFooter from './sections/CommunityFooter.vue'
import CommunitySignupDialog from './dialogs/CommunitySignupDialog.vue'
import { brand, venues } from '../../data/site.js'
import {
  communityStats as seedStats,
  ranking as seedRanking,
  stories as seedStories,
  communityEvents as seedEvents,
  currentChallenge as seedChallenge,
  venuePhotos,
} from '../../data/community.js'
import { listCommunity } from '../../services/api/index.js'
import { setSelection } from '../../services/selection.js'
import { HOME_STEPS as homeSteps, NAV_MAIN as navMain, ROUTE_IDS as routeIds } from '../../data/steps.js'

function normalizeEvent(event) {
  const cap = Number(event?.cap) || 0
  const signed = Math.min(cap, Number(event?.signed) || 0)
  const remaining = Number.isFinite(Number(event?.remaining))
    ? Number(event.remaining)
    : Math.max(0, cap - signed)
  return {
    ...event,
    venue: event?.venue || event?.venueName || '',
    cap,
    signed,
    remaining,
    soldOut: remaining <= 0 || Boolean(event?.soldOut),
  }
}

const stats = ref(seedStats)
const podium = ref(seedRanking)
const stories = ref(seedStories)
const events = ref(seedEvents.map(normalizeEvent))
const challenge = ref({ ...seedChallenge, joinedByMe: false })
const signedEventIds = ref([])
const challengeJoined = ref(false)

const venueCards = computed(() =>
  venues.map((venue) => ({ ...venue, img: venuePhotos[venue.code] || venue.image }))
)

const dialog = reactive({ open: false, kind: 'event', event: null })
const pageEl = ref(null)
const eventsEl = ref(null)
let observer = null
let originalTitle = ''

function handleGo(index, payload) {
  if (typeof window === 'undefined') return
  if (payload) setSelection(payload)
  window.location.hash = routeIds[index] ?? 'top'
}

function goHome() {
  handleGo(0)
}

function goBooking(withPlan = false) {
  if (withPlan) setSelection({ plan: 'PLAN-01' })
  handleGo(3)
}

function goVenue(code) {
  setSelection({ venue: code })
  handleGo(3)
}

function scrollTop() {
  pageEl.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

function scrollToEvents() {
  const section = eventsEl.value?.$el
  if (!section) return
  const header = document.querySelector('.volt-header')?.offsetHeight ?? 0
  pageEl.value?.scrollTo({ top: Math.max(0, section.offsetTop - header), behavior: 'smooth' })
}

function isSigned(event) {
  return Boolean(event?.mySigned) || signedEventIds.value.includes(event.id)
}

function openEvent(event) {
  if (isSigned(event) || event.soldOut) return
  dialog.kind = 'event'
  dialog.event = event
  dialog.open = true
}

function openChallenge() {
  if (challengeJoined.value || challenge.value.joinedByMe) return
  dialog.kind = 'challenge'
  dialog.event = null
  dialog.open = true
}

function closeDialog() {
  dialog.open = false
  dialog.event = null
}

function onJoined(payload) {
  if (payload.kind === 'event') {
    const index = events.value.findIndex((item) => item.id === payload.event.id)
    if (index >= 0) {
      const previous = events.value[index]
      events.value[index] = {
        ...previous,
        ...payload.event,
        venue: payload.event.venue || payload.event.venueName || previous.venue,
      }
    }
    if (!signedEventIds.value.includes(payload.event.id)) {
      signedEventIds.value = [...signedEventIds.value, payload.event.id]
    }
    return
  }
  if (challengeJoined.value) return
  challengeJoined.value = true
  challenge.value = {
    ...challenge.value,
    joined: Number(challenge.value.joined || 0) + 1,
    joinedByMe: true,
  }
}

async function refreshCommunity() {
  try {
    const data = await listCommunity()
    if (Array.isArray(data?.stats)) stats.value = data.stats
    if (Array.isArray(data?.ranking)) podium.value = data.ranking
    if (Array.isArray(data?.stories)) stories.value = data.stories
    if (Array.isArray(data?.events)) events.value = data.events.map(normalizeEvent)
    if (data?.challenge) {
      challenge.value = { ...challenge.value, ...data.challenge }
      if (data.challenge.joinedByMe) challengeJoined.value = true
    }
    if (data?.membership) {
      signedEventIds.value = [
        ...new Set([...signedEventIds.value, ...(data.membership.eventIds || [])]),
      ]
      if (data.membership.challengeJoined) challengeJoined.value = true
    }
    const serverSigned = (data?.events || []).filter((event) => event.mySigned)
    if (serverSigned.length) {
      signedEventIds.value = [
        ...new Set([...signedEventIds.value, ...serverSigned.map((event) => event.id)]),
      ]
    }
  } catch {
    /* keep the bundled data when the API is unreachable */
  } finally {
    nextTick(setupReveal)
  }
}

let reduced = false

function setupReveal() {
  if (!pageEl.value) return
  const items = pageEl.value.querySelectorAll('[data-reveal]')
  if (reduced || typeof IntersectionObserver === 'undefined') {
    items.forEach((el) => el.classList.add('is-in'))
    return
  }
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-in')
            observer.unobserve(entry.target)
          }
        })
      },
      { root: pageEl.value, threshold: 0.14, rootMargin: '0px 0px -40px 0px' }
    )
  }
  items.forEach((el) => {
    if (!el.classList.contains('is-in')) observer.observe(el)
  })
}

onMounted(() => {
  pageEl.value?.scrollTo({ top: 0 })
  originalTitle = document.title
  document.title = 'VOLT 社区 · 一起充能 | 广州三店'
  reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  setupReveal()
  refreshCommunity()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  if (originalTitle) document.title = originalTitle
})
</script>

<style scoped>
.community-page {
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--ink);
  color: var(--paper);
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, orange 40%, transparent) var(--ink);
}

.cm-main { padding-top: 0; }
</style>
