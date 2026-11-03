export const communityStats = [
  { value: '3,204', label: '在册社员', en: 'MEMBERS' },
  { value: '12', label: '本周活动', en: 'EVENTS' },
  { value: '84,206', label: '累计训练人次', en: 'WORKOUTS' },
]

export const ranking = [
  { no: '01', code: 'VOLT-0457', sessions: 23, minutes: 1720, weeks: 6, me: false },
  { no: '02', code: 'VOLT-1120', sessions: 21, minutes: 1585, weeks: 5, me: false },
  { no: '03', code: 'VOLT-0893', sessions: 19, minutes: 1460, weeks: 5, me: false },
  { no: '04', code: 'VOLT-2008', sessions: 17, minutes: 1295, weeks: 4, me: false },
  { no: '05', code: 'VOLT-3371', sessions: 15, minutes: 1180, weeks: 4, me: true },
]

export const stories = [
  {
    name: '阿 K',
    role: '搏击教练 · 天河工场',
    tag: '01',
    quote: '我不教你把动作做漂亮，我教你在一分钟后还站得住。',
    sessions: '640 节',
    streak: '28 周',
    favorite: '光影搏击',
    img: '/images/coach-03-960.webp',
  },
  {
    name: '林岚',
    role: '瑜伽教练 · 海珠 / 天河',
    tag: '02',
    quote: '练到能停下来，才算真的变强。别急，呼吸先跟上。',
    sessions: '412 节',
    streak: '19 周',
    favorite: '静流瑜伽',
    img: '/images/coach-02-960.webp',
  },
]

export const communityEvents = [
  {
    id: 'EV-1104',
    day: '04',
    month: 'NOV · WED',
    tag: 'WEEKDAY / 周三',
    title: '燃脂电流营',
    desc: 'HIIT 循环 + 心率带冲刺，一节课烧掉一周奶茶热量。',
    time: '周三 19:30',
    venueCode: 'V-01',
    venue: '天河工场',
    signed: 18,
    cap: 24,
  },
  {
    id: 'EV-1107',
    day: '07',
    month: 'NOV · SAT',
    tag: 'WEEKEND / 周六',
    title: '周末对决赛',
    desc: '两队 6v6 战绳接力，输的队请全场喝水。',
    time: '周六 14:00',
    venueCode: 'V-02',
    venue: '海珠工场',
    signed: 18,
    cap: 18,
  },
  {
    id: 'EV-1108',
    day: '08',
    month: 'NOV · SUN',
    tag: 'FRESH / 周日',
    title: '新手破冰课',
    desc: '零基础专场，教练 1 对 1 过器械，社恐友好。',
    time: '周日 10:00',
    venueCode: 'V-03',
    venue: '番禺工场',
    signed: 9,
    cap: 16,
  },
  {
    id: 'EV-1227',
    day: '27',
    month: 'DEC · SEASON',
    tag: 'SEASON / 季度',
    title: '季度总冲榜',
    desc: '季度最后一周，训练时长翻倍计点，榜首登门店墙。',
    time: '12 月 27–31',
    venueCode: 'V-01',
    venue: '三店同开',
    signed: 84,
    cap: 120,
  },
]

export const currentChallenge = {
  id: 'CH-3X-WEEK',
  title: '本周练满 3 次',
  desc: '完成后解锁「电流不断」限定贴纸，月底门店自取。',
  percent: 68,
  joined: 128,
  sessions: 3,
  days: 7,
}

export const venuePhotos = {
  'V-01': '/images/gym-space-960.webp',
  'V-02': '/images/hero-02-960.webp',
  'V-03': '/images/hero-03-960.webp',
}
