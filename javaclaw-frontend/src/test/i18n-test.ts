// Minimal i18n setup for tests (synchronous, no language detector).
import i18n from "i18next"
import { initReactI18next } from "react-i18next"

import en from "@/i18n/locales/en.json"

if (!i18n.isInitialized) {
  void i18n.use(initReactI18next).init({
    resources: { en: { translation: en } },
    lng: "en",
    fallbackLng: "en",
    interpolation: { escapeValue: false },
    returnNull: false,
  })
}

export default i18n
