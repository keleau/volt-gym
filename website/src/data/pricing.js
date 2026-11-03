import { brand } from './site.js'

export const pricingPlans = [
  {
    code: 'PLAN-01',
    name: '首次接入包',
    en: 'FIRST ACCESS',
    price: brand.trialPrice,
    unit: '一次性',
    featured: true,
    points: ['团课 1 节，任选课程', 'InBody 体测 1 次', '教练 1 对 1 训练建议', '三店通用，到期自动失效'],
    cta: 'RUN > 立即接入',
  },
  {
    code: 'PLAN-02',
    name: '按次充电',
    en: 'PAY PER SESSION',
    price: 99,
    unit: '每节起',
    featured: false,
    points: ['上一次付一次，不绑定年卡', '小程序自助选场次', '开课前 2 小时可退', '新人首节自动按 49 元结算'],
    cta: '查看本周场次',
  },
  {
    code: 'PLAN-03',
    name: '10 次充电卡',
    en: '10-PACK CARD',
    price: 899,
    unit: '10 节 / 180 天',
    featured: false,
    points: ['折合 89.9 元一节', '三店全部课程通用', '180 天长期有效', '可与朋友共享同一张卡'],
    cta: '购买次卡',
  },
]
