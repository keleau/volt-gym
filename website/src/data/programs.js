export const programs = [
  {
    no: 'P01',
    name: '光影搏击',
    en: 'SHADOW BOXING',
    desc: '出拳、躲闪、踢腿，把一天的压力打在空气里。',
    meta: { intensity: 'HIGH', duration: '60 MIN', kcal: '620 KCAL' },
  },
  {
    no: 'P02',
    name: '高压燃脂',
    en: 'HIGH VOLTAGE HIIT',
    desc: '短时高强度间歇，心率拉满，结束后还在持续烧。',
    meta: { intensity: 'HIGH', duration: '45 MIN', kcal: '550 KCAL' },
  },
  {
    no: 'P03',
    name: '力量搬运',
    en: 'STRENGTH WORKS',
    desc: '杠铃、壶铃、自重，把骨架和肌肉一点点练扎实。',
    meta: { intensity: 'MID', duration: '60 MIN', kcal: '430 KCAL' },
  },
  {
    no: 'P04',
    name: '战绳风暴',
    en: 'ROPE STORM',
    desc: '双绳甩动、波比和冲刺，把心率直接拽到红线。',
    meta: { intensity: 'MID', duration: '50 MIN', kcal: '580 KCAL' },
  },
  {
    no: 'P05',
    name: '静流瑜伽',
    en: 'QUIET FLOW',
    desc: '呼吸、拉伸、平衡，给充过头的身体放一次电。',
    meta: { intensity: 'LOW', duration: '60 MIN', kcal: '220 KCAL' },
  },
  {
    no: 'P06',
    name: '深度恢复',
    en: 'DEEP RECOVERY',
    desc: '泡沫轴、拉伸和呼吸，把紧绷的身体一点点放开。',
    meta: { intensity: 'LOW', duration: '55 MIN', kcal: '180 KCAL' },
  },
]

// 官网主推五门；P06 深度恢复保留在小程序与共用目录中。
export const featuredPrograms = programs.slice(0, 5)
