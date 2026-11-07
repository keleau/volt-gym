const dateUtil = require('./date')

function hasCard(member) {
  if (!member) return false
  return Number(member.creditsTotal) > 0 || Boolean(member.planCode)
}

function cardStatusText(member, pendingCard) {
  if (!member) return '未开通'
  if (!member.phoneBound) return '绑定后同步'
  if (pendingCard) return '待开卡'
  const credits = Number(member.credits) || 0
  if (!hasCard(member)) return credits > 0 ? `余 ${credits} 次` : '未开通'
  if (member.expiresAt && member.expiresAt < dateUtil.todayISO()) return '已过期'
  return credits > 0 ? `余 ${credits} 次` : '已用完'
}

module.exports = {
  hasCard,
  cardStatusText,
}
