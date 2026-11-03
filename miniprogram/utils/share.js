const auth = require('../services/auth')

const DEFAULT_IMAGE = '/assets/images/program-boxing.webp'

function inviteShare(options) {
  const opts = options || {}
  const user = auth.getUser()
  const ref = opts.ref || (user && user.referralCode) || ''
  const program = opts.program || 'P01'
  const params = [`ref=${ref}`, `program=${program}`]
  if (opts.from) params.push(`from=${encodeURIComponent(opts.from)}`)
  return {
    title: opts.title || '我在 VOLT 练到通电，送你一节 ¥49 体验课',
    path: `/pages/invite/index?${params.join('&')}`,
    imageUrl: opts.imageUrl || DEFAULT_IMAGE,
  }
}

function pageShare(title, path, imageUrl) {
  return {
    title,
    path,
    imageUrl: imageUrl || DEFAULT_IMAGE,
  }
}

module.exports = {
  DEFAULT_IMAGE,
  inviteShare,
  pageShare,
}
