import { afterEach, describe, expect, it } from 'vitest';
import { dictionary, interfaceLocale, msg, resolveInterfaceLocale, setInterfaceLanguage } from './index';
import { expandPseudoText, pseudoMessages } from './pseudo';
import { supportedInterfaceLocales } from './types';

const englishKeys = Object.keys(dictionary('en')).sort();

afterEach(() => setInterfaceLanguage('EN'));

describe('interface localization', () => {
  it('requires every production locale to implement the same typed message set', () => {
    for (const locale of supportedInterfaceLocales) {
      expect(Object.keys(dictionary(locale)).sort()).toEqual(englishKeys);
    }
  });

  it('resolves explicit, regional system, and unsupported system locales', () => {
    expect(resolveInterfaceLocale('DE', ['fr-CA'])).toBe('de');
    expect(resolveInterfaceLocale('AUTO', ['fr-CA'])).toBe('fr');
    expect(resolveInterfaceLocale('AUTO', ['ja-JP', 'es-MX'])).toBe('es');
    expect(resolveInterfaceLocale('AUTO', ['ja-JP'])).toBe('en');
  });

  it('updates reactive locale state and the document language', () => {
    expect(setInterfaceLanguage('AUTO', ['it-IT'])).toBe('it');
    expect(interfaceLocale.value).toBe('it');
    expect(document.documentElement.lang).toBe('it');
    expect(msg('settingsTitle')).toBe('IMPOSTAZIONI');
  });

  it('formats typed message parameters with interface-locale plurals', () => {
    setInterfaceLanguage('DE');
    expect(msg('gamesFound', '1', 'one')).toBe('1 Spiel gefunden.');
    expect(msg('gamesFound', '1.000', 'other')).toBe('1.000 Spiele gefunden.');
  });

  it('provides a visibly delimited expansion-only pseudo dictionary', () => {
    expect(expandPseudoText('Settings')).toBe('⟦Seettiings⟧');
    expect(pseudoMessages.settingsTitle).toMatch(/^⟦.+⟧$/);
    expect(pseudoMessages.gamesFound('12', 'other')).toMatch(/^⟦.+⟧$/);
  });
});
