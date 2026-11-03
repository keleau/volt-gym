<template>
  <BaseModal :open="true" @close="$emit('close')">
    <div class="modal-panel" role="dialog" aria-modal="true" aria-label="预约成功">
      <p class="modal-status mono">BOOKING CONFIRMED / 预约已确认</p>
      <h3>名额已锁定。</h3>
      <p class="modal-plan mono">{{ booking.planCode }} / {{ booking.planName }}</p>
      <p v-if="memberSession" class="modal-member mono">
        已记入会员账号 · {{ memberSession.member.planName }} · 余 {{ memberSession.member.credits }} 次
      </p>
      <dl class="modal-detail mono">
        <div><dt>预约编号</dt><dd>{{ booking.orderNo }}</dd></div>
        <div><dt>姓名</dt><dd>{{ booking.name }}</dd></div>
        <div><dt>充电站</dt><dd>{{ booking.venueName }}</dd></div>
        <div><dt>课程</dt><dd>{{ booking.programName }}</dd></div>
        <div><dt>日期</dt><dd>{{ booking.date }}</dd></div>
        <div v-if="booking.time">
          <dt>时间</dt>
          <dd>{{ booking.time }}<template v-if="booking.endTime">–{{ booking.endTime }}</template></dd>
        </div>
        <div><dt>金额</dt><dd>{{ memberSession ? '会员次卡' : '¥' + booking.amount }}</dd></div>
      </dl>
      <p class="modal-note">预约信息已记录，凭手机号到店核销。需要改时间，提前 2 小时来电即可。</p>
      <button type="button" class="modal-btn" @click="$emit('close')">完成</button>
    </div>
  </BaseModal>
</template>

<script setup>
import BaseModal from '../../ui/BaseModal.vue'

defineProps({
  booking: { type: Object, required: true },
  memberSession: { type: Object, default: null },
})
defineEmits(['close'])
</script>

<style scoped>
.modal-panel {
  width: min(500px, 100%);
  padding: 24px;
  background: var(--paper);
  color: var(--ink);
  border: 1px solid color-mix(in srgb, #F59E0B 54%, transparent);
  box-shadow: 10px 10px 0 color-mix(in srgb, #F59E0B 48%, #0D0B08);
}
.modal-status {
  display: inline-block;
  margin: 0;
  padding: 6px 9px;
  border: 1px solid color-mix(in srgb, #F59E0B 56%, transparent);
  color: #8A5200;
  font-size: 9px;
  letter-spacing: .06em;
}
.modal-panel h3 {
  margin: 14px 0 3px;
  font-family: var(--font-cn);
  font-size: 29px;
  font-weight: 900;
  letter-spacing: -.04em;
}
.modal-plan {
  margin: 0 0 13px;
  font-size: 8px;
  letter-spacing: .06em;
  color: #8A5200;
}
.modal-member {
  margin: -6px 0 13px;
  padding: 6px 9px;
  border-left: 3px solid var(--acid);
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  font-size: 8px;
  line-height: 1.5;
  color: #7A3B00;
}
.modal-detail { margin: 0; display: flex; flex-direction: column; gap: 7px; }
.modal-detail div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 6px;
  border-bottom: 1px dashed color-mix(in srgb, #0D0B08 18%, transparent);
  font-size: 10px;
}
.modal-detail dt { color: color-mix(in srgb, #0D0B08 72%, transparent); }
.modal-detail dd { margin: 0; font-weight: 500; text-align: right; }
.modal-note {
  margin: 13px 0 15px;
  font-family: var(--font-cn);
  font-size: 13px;
  line-height: 1.6;
  color: color-mix(in srgb, #0D0B08 72%, transparent);
}
.modal-btn {
  width: 100%;
  min-height: 44px;
  background: var(--acid);
  color: var(--ink);
  font-family: var(--font-cn);
  font-size: 13px;
  font-weight: 900;
  transition: background .2s ease, color .2s ease;
}
.modal-btn:hover { background: var(--ink); color: var(--acid); }

@media (max-width: 900px) {
  .modal-panel { padding: 19px; }
  .modal-panel h3 { font-size: 24px; }
}
</style>
