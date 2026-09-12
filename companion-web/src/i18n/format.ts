import { interfaceLocale } from './index';
import type { SupportedInterfaceLocale } from './types';

export function formatUiNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(interfaceLocale.value, options).format(value);
}

export function formatUiDate(value: number | Date, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(interfaceLocale.value, options).format(value);
}

export function normalizeInterfaceText(value: string): string {
  return value.normalize('NFKD').toLocaleLowerCase(interfaceLocale.value);
}

export function normalizeRomText(value: string, contentLanguage?: string | null): string {
  const locale = contentLanguage?.trim() || interfaceLocale.value;
  return value.normalize('NFKD').toLocaleLowerCase(locale);
}

export function pluralCategory(value: number, locale: SupportedInterfaceLocale = interfaceLocale.value): Intl.LDMLPluralRule {
  return new Intl.PluralRules(locale).select(value);
}
