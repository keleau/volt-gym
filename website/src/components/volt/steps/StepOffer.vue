<template>
  <div class="screen-inner offer-split" data-scrollable>
    <OfferPlans
      :plans="pricingPlans"
      :selected="selectedPlan.code"
      :active="active"
      @select="selectPlan"
    />
    <CardRegisterForm
      v-if="selectedPlan.kind === 'card'"
      :active="active"
      :selected-plan="selectedPlan"
    />
    <BookingForm
      v-else
      :active="active"
      :preset="preset"
      :selected-plan="selectedPlan"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import OfferPlans from '../offer/OfferPlans.vue'
import BookingForm from '../offer/BookingForm.vue'
import CardRegisterForm from '../offer/CardRegisterForm.vue'
import { pricingPlans } from '../../../data/pricing.js'
import { setSelection } from '../../../services/selection.js'

const props = defineProps({
  active: { type: Boolean, default: false },
  preset: { type: Object, default: () => ({ program: '', venue: '', plan: '' }) },
})

const selectedPlan = ref(
  pricingPlans.find((plan) => plan.code === props.preset.plan) || pricingPlans[0]
)

function selectPlan(plan) {
  selectedPlan.value = plan
}

watch(
  () => props.preset.plan,
  (code) => {
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
