import { messagesEn } from './messages.en';
import type { MessageDictionary } from './types';

export function expandPseudoText(value: string): string {
  const expanded = value.replace(/[aeiouAEIOU]/g, vowel => `${vowel}${vowel.toLowerCase()}`);
  return `⟦${expanded}⟧`;
}

export const pseudoMessages = Object.fromEntries(
  Object.entries(messagesEn).map(([key, value]) => [key, expandPseudoText(value)]),
) as unknown as MessageDictionary;
