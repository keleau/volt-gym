<template>
  <div class="screen-inner offer-split" data-scrollable>
    <OfferPlans
      :plans="pricingPlans"
      :selected="selectedPlan.code"
      :active="active"
      :locked="memberActive"
      @select="selectPlan"
    />
    <BookingForm
      :active="active"
      :preset="preset"
      :selected-plan="selectedPlan"
      @select-plan="selectPlan"
      @member-change="memberActive = $event"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import OfferPlans from '../offer/OfferPlans.vue'
import BookingForm from '../offer/BookingForm.vue'
import { pricingPlans } from '../../../data/pricing.js'
import { setSelection } from '../../../services/selection.js'

const props = defineProps({
  active: { type: Boolean, default: false },
  preset: { type: Object, default: () => ({ program: '', venue: '', plan: '' }) },
})

const selectedPlan = ref(
  pricingPlans.find((plan) => plan.code === props.preset.plan) || pricingPlans[0]
)
const memberActive = ref(false)

function selectPlan(plan) {
  selectedPlan.value = plan
}

watch(
  () => props.preset.plan,
  (code) => {
    // 会员登录后价格由会员次卡接管，导航 CTA 不能把方案改回体验包。
    if (memberActive.value) return
    const next = pricingPlans.find((plan) => plan.code === code)
    if (next) selectedPlan.value = next
  }
)

watch(
  () => selectedPlan.value.code,
  (plan) => setSelection({ plan })
)
</script>

<style scoped>
.offer-split {
  display: grid;
  grid-template-columns: minmax(0, .82fr) minmax(500px, 1.18fr);
  gap: clamp(24px, 3.8vw, 64px);
  align-items: stretch;
  color: var(--paper);
  overflow-y: auto;
  scrollbar-width: thin;
}

@media (max-width: 1100px) and (min-width: 901px) {
  .offer-split { grid-template-columns: minmax(0, .78fr) minmax(450px, 1.22fr); gap: 22px; }
}

@media (max-width: 900px) {
  .offer-split {
    display: flex;
    flex-direction: column;
    gap: 16px;
    overflow-y: auto;
  }
}
</style>
