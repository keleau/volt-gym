import { brand } from './site.js'

export const pricingPlans = [
  {
    code: 'PLAN-01',
    kind: 'trial',
    name: '首次体验课',
    en: 'FIRST TRIAL',
    price: brand.trialPrice,
    unit: '单次',
    featured: true,
    points: [
      '团课 1 节，可约时段',
      'InBody 体测 1 次',
      '教练 1 对 1 训练建议',
      '三店通用，到店付款',
    ],
    cta: '预约体验课',
  },
  {
    code: 'PLAN-03',
    kind: 'card',
    name: '10 次充电卡',
    en: '10-PACK CARD',
    price: 899,
    unit: '10 节 / 180 天',
    credits: 10,
    validDays: 180,
    featured: false,
    points: [
      '折合 89.9 元一节',
      '三店全部课程通用',
      '180 天长期有效',
      '可与朋友共享同一张卡',
    ],
    cta: '登记购卡',
  },
]
