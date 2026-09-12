import { signal } from '@preact/signals';
import { messagesDe } from './messages.de';
import { messagesEn } from './messages.en';
import { messagesEs } from './messages.es';
import { messagesFr } from './messages.fr';
import { messagesIt } from './messages.it';
import type { InterfaceLanguageSetting, MessageDictionary, MessageKey, SupportedInterfaceLocale } from './types';

export * from './format';
export * from './types';

const messages: Record<SupportedInterfaceLocale, MessageDictionary> = {
  de: messagesDe,
  en: messagesEn,
  es: messagesEs,
  fr: messagesFr,
  it: messagesIt,
};

export const interfaceLocale = signal<SupportedInterfaceLocale>('en');

export function resolveInterfaceLocale(
  setting: InterfaceLanguageSetting | null | undefined,
  systemLocales: readonly string[] = typeof navigator === 'undefined' ? [] : navigator.languages,
): SupportedInterfaceLocale {
  if (setting && setting !== 'AUTO') return setting.toLowerCase() as SupportedInterfaceLocale;
  for (const locale of systemLocales) {
    const language = locale.trim().toLowerCase().split(/[-_]/, 1)[0] as SupportedInterfaceLocale;
    if (language in messages) return language;
  }
  return 'en';
}

export function setInterfaceLanguage(
  setting: InterfaceLanguageSetting | null | undefined,
  systemLocales?: readonly string[],
): SupportedInterfaceLocale {
  const locale = resolveInterfaceLocale(setting, systemLocales);
  interfaceLocale.value = locale;
  if (typeof document !== 'undefined') document.documentElement.lang = locale;
  return locale;
}

type MessageArgs<Key extends MessageKey> = MessageDictionary[Key] extends (...args: infer Args) => string ? Args : [];
type MessageResult<Key extends MessageKey> = MessageDictionary[Key] extends (...args: infer _Args) => infer Result
  ? Result
  : MessageDictionary[Key];

export function msg<Key extends MessageKey>(key: Key, ...args: MessageArgs<Key>): MessageResult<Key> {
  const message = messages[interfaceLocale.value][key];
  if (typeof message === 'function') {
    return (message as unknown as (...values: MessageArgs<Key>) => MessageResult<Key>)(...args);
  }
  return message as MessageResult<Key>;
}

export function dictionary(locale: SupportedInterfaceLocale): Readonly<MessageDictionary> {
  return messages[locale];
}
