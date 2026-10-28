import { computed, ref } from 'vue'
import { createI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import enEl from 'element-plus/es/locale/lang/en'
import zhEl from 'element-plus/es/locale/lang/zh-cn'
import en from './en'
import zh from './zh'

export type Lang = 'en' | 'zh'
const STORAGE_KEY = 'orderpilot.lang'

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'en' || saved === 'zh') return saved
  } catch {
    /* storage unavailable */
  }
  return navigator.language?.toLowerCase().startsWith('zh') ? 'zh' : 'en'
}

const start = initialLang()

export const i18n = createI18n({
  legacy: false,
  locale: start,
  fallbackLocale: 'en',
  messages: { en, zh },
})

const current = ref<Lang>(start)
export const lang = computed(() => current.value)
export const elementLocale = computed(() => (current.value === 'zh' ? zhEl : enEl))

function apply(l: Lang) {
  i18n.global.locale.value = l
  dayjs.locale(l === 'zh' ? 'zh-cn' : 'en')
  document.documentElement.lang = l === 'zh' ? 'zh-CN' : 'en'
}

export function setLang(l: Lang) {
  current.value = l
  apply(l)
  try {
    localStorage.setItem(STORAGE_KEY, l)
  } catch {
    /* storage unavailable */
  }
}

apply(start)

/** For code outside components (stores, formatters, http). */
export const t = i18n.global.t
