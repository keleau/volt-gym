<template>
  <BaseModal :open="open" @close="$emit('close')">
    <div class="member-modal" role="dialog" aria-modal="true" aria-label="会员登录与会员卡">
      <div class="member-modal-head">
        <div>
          <p class="mono">MEMBER / 会员</p>
          <h3>登录看你的卡<em>。</em></h3>
        </div>
        <button type="button" class="member-modal-close" aria-label="关闭" @click="$emit('close')">
          ×
        </button>
      </div>
      <WebMemberPanel @change="$emit('change', $event)" @mini="$emit('mini')" />
      <p class="member-modal-note mono">
        会员卡只是账号；次卡需到店付款开卡后生效。
      </p>
    </div>
  </BaseModal>
</template>

<script setup>
import BaseModal from '../../ui/BaseModal.vue'
import WebMemberPanel from './WebMemberPanel.vue'

defineProps({
  open: { type: Boolean, default: false },
})
defineEmits(['close', 'change', 'mini'])
</script>

<style scoped>
.member-modal {
  width: min(500px, 100%);
  padding: 22px;
  border: 1px solid color-mix(in srgb, #F59E0B 54%, transparent);
  background: var(--paper);
  color: var(--ink);
  box-shadow: 10px 10px 0 color-mix(in srgb, #F59E0B 48%, #0D0B08);
}
.member-modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.member-modal-head p {
  margin: 0;
  font-size: 8px;
  letter-spacing: .1em;
  color: #8A5200;
}
.member-modal-head h3 {
  margin: 5px 0 0;
  font-family: var(--font-cn);
  font-size: 27px;
  font-weight: 900;
  line-height: 1.05;
  letter-spacing: -.04em;
}
.member-modal-head h3 em { font-style: normal; color: var(--acid); }
.member-modal-close {
  flex: none;
  width: 32px;
  height: 32px;
  border: 1px solid color-mix(in srgb, #0D0B08 24%, transparent);
  color: var(--ink);
  font-size: 20px;
  line-height: 1;
}
.member-modal-close:hover { background: #0D0B08; color: var(--paper); }
.member-modal-note {
  margin: 12px 0 0;
  font-size: 8px;
  line-height: 1.5;
  color: color-mix(in srgb, #0D0B08 58%, transparent);
}

@media (max-width: 900px) {
  .member-modal { padding: 18px; }
  .member-modal-head h3 { font-size: 23px; }
}
</style>
