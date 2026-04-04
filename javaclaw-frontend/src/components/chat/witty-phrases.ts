/**
 * Witty loading phrases — curated selection ported from QwenLM/qwen-code
 * (packages/cli/src/i18n/locales/{en,ru}.js). Original license: Apache-2.0,
 * Copyright 2025 Google LLC. Cycled every 15s while awaiting assistant.
 */

export const WITTY_PHRASES_EN: string[] = [
  "Warming up the AI hamsters…",
  "Consulting the digital spirits…",
  "Reticulating splines…",
  "Brewing fresh bytes…",
  "Engaging cognitive processors…",
  "Polishing the algorithms…",
  "Untangling neural nets…",
  "Compiling brilliance…",
  "Summoning the cloud of wisdom…",
  "Confuzzling the options…",
  "Tuning the cosmic frequencies…",
  "Defragmenting memories…",
  "Converting coffee into code…",
  "Rewiring the synapses…",
  "Looking for a misplaced semicolon…",
  "Calibrating the flux capacitor…",
  "Engaging the improbability drive…",
  "Aligning the stars for optimal response…",
  "Channeling the Force…",
  "Warp speed engaged…",
  "Don't panic…",
  "Following the white rabbit…",
  "Loading wit.exe…",
  "Caching the essentials…",
  "Garbage collecting…",
]

export const WITTY_PHRASES_RU: string[] = [
  "Разогреваем ИИ-хомячков…",
  "Советуемся с цифровыми духами…",
  "Сглаживаем сплайны…",
  "Завариваем свежие байты…",
  "Задействуем когнитивные процессоры…",
  "Полируем алгоритмы…",
  "Распутываем нейросети…",
  "Компилируем гениальность…",
  "Призываем облако мудрости…",
  "Запутываем варианты…",
  "Настраиваем космические частоты…",
  "Дефрагментируем память…",
  "Превращаем кофе в код…",
  "Переподключаем синапсы…",
  "Ищем лишнюю точку с запятой…",
  "Калибруем потоковый накопитель…",
  "Включаем двигатель невероятности…",
  "Выравниваем звёзды…",
  "Направляем Силу…",
  "Варп-прыжок активирован…",
  "Без паники…",
  "Следуем за белым кроликом…",
  "Загружаем yumor.exe…",
  "Кэшируем важное…",
  "Собираем мусор…",
  "Продуваем картридж…",
  "Ждём респауна…",
]

export function pickWittyPhrases(lang: string): string[] {
  return lang.toLowerCase().startsWith("ru")
    ? WITTY_PHRASES_RU
    : WITTY_PHRASES_EN
}
