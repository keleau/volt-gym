#!/usr/bin/env node
/**
 * VOLT 契约自检：对着真实后端跑 docs/web-integration.md §11 的验收清单。
 * 用法：node tools/api-check.mjs [baseUrl]
 * 默认 baseUrl = http://localhost:8080/api/v1
 */
const BASE = (process.argv[2] || 'http://localhost:8080/api/v1').replace(/\/+$/, '')

const results = []
let failures = 0

function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  if (!ok) failures += 1
  const mark = ok ? 'PASS' : 'FAIL'
  console.log(`${mark}  ${name}${detail ? `  -> ${detail}` : ''}`)
}

async function call(path, { method = 'GET', body, headers = {} } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  let json = null
  const text = await res.text()
  if (text) {
    try {
      json = JSON.parse(text)
    } catch {
      json = null
    }
  }
  return { status: res.status, json, headers: res.headers }
}

function isoDate(offsetDays) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return d.toISOString().slice(0, 10)
}

const stamp = Date.now().toString().slice(-8)
const phone = `139${stamp}`
const phone2 = `138${stamp}`

async function main() {
  console.log(`\nVOLT 契约自检 -> ${BASE}\n`)

  // ---------- 目录 ----------
  const venues = await call('/catalog/venues')
  check('GET /catalog/venues 200', venues.status === 200, `status=${venues.status}`)
  check('门店数量为 3', venues.json?.venues?.length === 3, `count=${venues.json?.venues?.length}`)
  check('门店带 todaySlots', typeof venues.json?.venues?.[0]?.todaySlots === 'number',
    `todaySlots=${venues.json?.venues?.[0]?.todaySlots}`)

  const programs = await call('/catalog/programs')
  check('GET /catalog/programs 200', programs.status === 200, `status=${programs.status}`)
  check('课程数量为 6', programs.json?.programs?.length === 6, `count=${programs.json?.programs?.length}`)
  check('课程 meta.duration 为字符串', typeof programs.json?.programs?.[0]?.meta?.duration === 'string',
    `duration=${programs.json?.programs?.[0]?.meta?.duration}`)

  const plans = await call('/catalog/plans')
  check('GET /catalog/plans 200', plans.status === 200, `status=${plans.status}`)
  check('次卡数量为 3', plans.json?.plans?.length === 3, `count=${plans.json?.plans?.length}`)
  check('次卡 points 为数组', Array.isArray(plans.json?.plans?.[0]?.points),
    `points=${JSON.stringify(plans.json?.plans?.[0]?.points)}`)

  // ---------- 余位 ----------
  // 每次跑在不同日期上，避免反复自检把同一天的座位占满
  const date = isoDate(25 + Math.floor(Math.random() * 26))
  const availability = await call(`/availability?venue=V-01&program=P02&date=${date}`)
  check('GET /availability 200', availability.status === 200, `status=${availability.status}`)
  check('余位 remaining = capacity - booked',
    availability.json?.remaining === availability.json?.capacity - availability.json?.booked,
    `cap=${availability.json?.capacity} booked=${availability.json?.booked} remaining=${availability.json?.remaining}`)
  check('soldOut 与 remaining 一致',
    availability.json?.soldOut === (availability.json?.remaining <= 0), `soldOut=${availability.json?.soldOut}`)
  check('sessions[] 可用', Array.isArray(availability.json?.sessions) && availability.json.sessions.length > 0,
    `count=${availability.json?.sessions?.length}`)

  const badVenue = await call('/availability?venue=V-99&program=P02&date=' + date)
  check('未知门店 404 NOT_FOUND', badVenue.status === 404 && badVenue.json?.code === 'NOT_FOUND',
    `${badVenue.status}/${badVenue.json?.code}`)

  const badProgram = await call('/availability?venue=V-01&program=P99&date=' + date)
  check('未知课程 404 NOT_FOUND', badProgram.status === 404 && badProgram.json?.code === 'NOT_FOUND',
    `${badProgram.status}/${badProgram.json?.code}`)

  const pastDate = await call(`/availability?venue=V-01&program=P02&date=${isoDate(-1)}`)
  check('过去日期 400 DATE_OUT_OF_RANGE', pastDate.status === 400 && pastDate.json?.code === 'DATE_OUT_OF_RANGE',
    `${pastDate.status}/${pastDate.json?.code}`)

  const farDate = await call(`/availability?venue=V-01&program=P02&date=${isoDate(90)}`)
  check('超出 60 天 400 DATE_OUT_OF_RANGE', farDate.status === 400 && farDate.json?.code === 'DATE_OUT_OF_RANGE',
    `${farDate.status}/${farDate.json?.code}`)

  // ---------- 预约 ----------
  const idem = `idem-${stamp}`
  const bookingBody = {
    planCode: 'PLAN-01',
    name: '林一',
    phone,
    venueCode: 'V-01',
    programNo: 'P02',
    date,
    autoConfirm: true,
  }
  const created = await call('/bookings', { method: 'POST', body: bookingBody, headers: { 'Idempotency-Key': idem } })
  check('POST /bookings 201', created.status === 201, `status=${created.status} ${JSON.stringify(created.json?.message || '')}`)
  check('返回 booking + session', Boolean(created.json?.booking && created.json?.session),
    `booking=${Boolean(created.json?.booking)} session=${Boolean(created.json?.session)}`)
  check('booking.status = confirmed', created.json?.booking?.status === 'confirmed', `status=${created.json?.booking?.status}`)
  check('orderNo 非空', Boolean(created.json?.booking?.orderNo), `orderNo=${created.json?.booking?.orderNo}`)
  check('手机号脱敏', /^1[3-9]\d\*{4}\d{4}$/.test(created.json?.booking?.phone || ''), `phone=${created.json?.booking?.phone}`)
  check('amount 取自方案价格', created.json?.booking?.amount === 49, `amount=${created.json?.booking?.amount}`)
  check('autoConfirm 生效（已确认）', created.json?.booking?.status === 'confirmed')

  const after = await call(`/availability?venue=V-01&program=P02&date=${date}`)
  check('下单后余位减少 1',
    after.json?.remaining === availability.json?.remaining - 1,
    `before=${availability.json?.remaining} after=${after.json?.remaining}`)

  const replay = await call('/bookings', { method: 'POST', body: bookingBody, headers: { 'Idempotency-Key': idem } })
  check('相同 Idempotency-Key 重放同一单',
    replay.status === 201 && replay.json?.booking?.orderNo === created.json?.booking?.orderNo,
    `${replay.status} orderNo=${replay.json?.booking?.orderNo}`)

  const duplicate = await call('/bookings', {
    method: 'POST',
    body: { ...bookingBody, sessionId: created.json?.booking?.sessionId },
    headers: { 'Idempotency-Key': `idem-dup-${stamp}` },
  })
  check('重复预约 409 DUPLICATE_BOOKING', duplicate.status === 409 && duplicate.json?.code === 'DUPLICATE_BOOKING',
    `${duplicate.status}/${duplicate.json?.code}`)

  const badPhone = await call('/bookings', {
    method: 'POST',
    body: { ...bookingBody, phone: '123' },
    headers: { 'Idempotency-Key': `idem-badphone-${stamp}` },
  })
  check('手机号非法 400 且 fields.phone',
    badPhone.status === 400 && badPhone.json?.code === 'VALIDATION_ERROR' && Boolean(badPhone.json?.fields?.phone),
    `${badPhone.status} fields=${JSON.stringify(badPhone.json?.fields)}`)

  const badPlan = await call('/bookings', {
    method: 'POST',
    body: { ...bookingBody, planCode: 'PLAN-99' },
    headers: { 'Idempotency-Key': `idem-badplan-${stamp}` },
  })
  check('方案不存在 400 且 fields.planCode',
    badPlan.status === 400 && Boolean(badPlan.json?.fields?.planCode),
    `${badPlan.status} fields=${JSON.stringify(badPlan.json?.fields)}`)

  const badDate = await call('/bookings', {
    method: 'POST',
    body: { ...bookingBody, date: isoDate(90) },
    headers: { 'Idempotency-Key': `idem-baddate-${stamp}` },
  })
  check('超出窗口 400 DATE_OUT_OF_RANGE',
    badDate.status === 400 && badDate.json?.code === 'DATE_OUT_OF_RANGE',
    `${badDate.status}/${badDate.json?.code}`)

  const full = await call('/bookings', {
    method: 'POST',
    body: { ...bookingBody, phone: phone2, venueCode: 'V-03', programNo: 'P06' },
    headers: { 'Idempotency-Key': `idem-full-${stamp}` },
  })
  check('满员场次 409 SLOT_FULL', full.status === 409 && full.json?.code === 'SLOT_FULL',
    `${full.status}/${full.json?.code}`)

  // ---------- 社区 ----------
  const community = await call('/community')
  check('GET /community 200', community.status === 200, `status=${community.status}`)
  check('社区含 events / challenge / membership',
    Array.isArray(community.json?.events) && Boolean(community.json?.challenge) && Boolean(community.json?.membership),
    `events=${community.json?.events?.length}`)
  check('匿名 membership 为空',
    (community.json?.membership?.eventIds || []).length === 0 && community.json?.membership?.challengeJoined === false)
  const allEvents = community.json?.events || []
  const ev = allEvents[0]
  check('活动 remaining = cap - signed',
    ev?.remaining === ev?.cap - ev?.signed, `cap=${ev?.cap} signed=${ev?.signed} remaining=${ev?.remaining}`)

  // 报名用「还有位」的活动，满员校验用「已满」的活动，避免写死某个 id
  const openEvent = allEvents.find((item) => item.remaining > 0)
  const fullEvent = allEvents.find((item) => item.remaining <= 0)

  if (openEvent) {
    const signup = await call(`/events/${openEvent.id}/signups`, {
      method: 'POST',
      body: { name: '陈舟', phone },
      headers: { 'Idempotency-Key': `idem-sign-${stamp}` },
    })
    check('POST /events/:id/signups 200', signup.status === 200, `status=${signup.status} event=${openEvent.id}`)
    check('报名返回 signupNo', Boolean(signup.json?.signupNo), `signupNo=${signup.json?.signupNo}`)
    check('报名后 signed +1', signup.json?.event?.signed === openEvent.signed + 1,
      `before=${openEvent.signed} after=${signup.json?.event?.signed}`)
    check('报名后 mySigned=true', signup.json?.event?.mySigned === true)

    const dupSignup = await call(`/events/${openEvent.id}/signups`, {
      method: 'POST',
      body: { name: '陈舟', phone },
      headers: { 'Idempotency-Key': `idem-sign2-${stamp}` },
    })
    check('重复报名 409 DUPLICATE_SIGNUP',
      dupSignup.status === 409 && dupSignup.json?.code === 'DUPLICATE_SIGNUP',
      `${dupSignup.status}/${dupSignup.json?.code}`)
  } else {
    check('POST /events/:id/signups 200', false, '没有剩余名额的活动，先重置示例数据')
  }

  if (fullEvent) {
    const fullSignup = await call(`/events/${fullEvent.id}/signups`, {
      method: 'POST',
      body: { name: '陈舟', phone: phone2 },
      headers: { 'Idempotency-Key': `idem-signfull-${stamp}` },
    })
    check('满员活动 409 EVENT_FULL',
      fullSignup.status === 409 && fullSignup.json?.code === 'EVENT_FULL',
      `${fullSignup.status}/${fullSignup.json?.code} event=${fullEvent.id}`)
  } else {
    check('满员活动 409 EVENT_FULL', false, '示例数据里没有满员活动')
  }

  const challenge = await call('/challenges/current/join', {
    method: 'POST',
    body: { name: '陈舟', phone },
    headers: { 'Idempotency-Key': `idem-ch-${stamp}` },
  })
  check('POST /challenges/current/join 200', challenge.status === 200, `status=${challenge.status}`)
  check('挑战返回 memberNo', Boolean(challenge.json?.memberNo), `memberNo=${challenge.json?.memberNo}`)
  check('首次加入 already=false', challenge.json?.already === false, `already=${challenge.json?.already}`)

  const challengeAgain = await call('/challenges/current/join', {
    method: 'POST',
    body: { name: '陈舟', phone },
    headers: { 'Idempotency-Key': `idem-ch2-${stamp}` },
  })
  check('重复加入 200 already=true', challengeAgain.status === 200 && challengeAgain.json?.already === true,
    `${challengeAgain.status} already=${challengeAgain.json?.already}`)

  const communityAfter = await call('/community')
  check('社区 membership 仍为匿名空态', (communityAfter.json?.membership?.eventIds || []).length === 0)

  // ---------- 登录态 ----------
  const otp = await call('/auth/otp/send', { method: 'POST', body: { phone } })
  check('POST /auth/otp/send 200', otp.status === 200, `status=${otp.status}`)

  const noToken = await call('/member/bookings')
  check('无 token 访问会员台账 401 UNAUTHORIZED',
    noToken.status === 401 && noToken.json?.code === 'UNAUTHORIZED',
    `${noToken.status}/${noToken.json?.code}`)

  const staleToken = await call('/community', { headers: { Authorization: 'Bearer stale-token' } })
  check('失效 token 访问社区 401（让前端清会话）',
    staleToken.status === 401 && staleToken.json?.code === 'UNAUTHORIZED',
    `${staleToken.status}/${staleToken.json?.code}`)

  if (otp.json?.devCode) {
    const verify = await call('/auth/otp/verify', { method: 'POST', body: { phone, code: otp.json.devCode } })
    check('POST /auth/otp/verify 200', verify.status === 200, `status=${verify.status}`)
    check('登录返回 token + member', Boolean(verify.json?.token && verify.json?.member?.id),
      `token=${Boolean(verify.json?.token)} member=${verify.json?.member?.id}`)
    if (verify.json?.token) {
      const mine = await call('/member/bookings', { headers: { Authorization: `Bearer ${verify.json.token}` } })
      check('带 token 会员台账 200', mine.status === 200, `status=${mine.status}`)
      check('会员台账含本次预约', (mine.json?.upcoming?.length || 0) >= 1,
        `upcoming=${mine.json?.upcoming?.length}`)
      const badToken = await call('/member/bookings', { headers: { Authorization: 'Bearer nope' } })
      check('无效 token 401', badToken.status === 401, `${badToken.status}`)
    }
  } else {
    console.log('SKIP  验证码登录相关检查（后端未开启 volt.auth.expose-otp）')
  }

  // ---------- CORS ----------
  const preflight = await fetch(`${BASE}/bookings`, {
    method: 'OPTIONS',
    headers: {
      Origin: 'http://localhost:5176',
      'Access-Control-Request-Method': 'POST',
      'Access-Control-Request-Headers': 'content-type,idempotency-key,authorization',
    },
  })
  check('CORS 预检通过', preflight.status === 204 || preflight.status === 200, `status=${preflight.status}`)
  check('CORS 回显允许来源', Boolean(preflight.headers.get('access-control-allow-origin')),
    `origin=${preflight.headers.get('access-control-allow-origin')}`)

  console.log(`\n结果：${results.length - failures}/${results.length} 通过，失败 ${failures} 项\n`)
  process.exit(failures ? 1 : 0)
}

main().catch((error) => {
  console.error('自检脚本异常：', error)
  process.exit(2)
})
