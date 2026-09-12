export const supportedInterfaceLocales = ['en', 'fr', 'de', 'it', 'es'] as const;

export type SupportedInterfaceLocale = typeof supportedInterfaceLocales[number];
export type InterfaceLanguageSetting = 'AUTO' | 'EN' | 'FR' | 'DE' | 'IT' | 'ES';

export interface MessageValues {
  settingsTitle: string;
  settingsCategories: string;
  interfaceLanguage: string;
  interfaceLanguageNote: string;
  languageAuto: string;
  languageEnglish: string;
  languageFrench: string;
  languageGerman: string;
  languageItalian: string;
  languageSpanish: string;
}

export type MessageKey = keyof MessageValues;
export type MessageDictionary = { [Key in MessageKey]: MessageValues[Key] };
