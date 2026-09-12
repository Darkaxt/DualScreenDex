import { msg } from './i18n';

export const gameplayCopy = {
  get dataUnavailable() { return msg('dataUnavailable'); },
  get catchForEntry() { return msg('catchForEntry'); },
  get catchForFullData() { return msg('catchForFullData'); },
  get pokedexUnavailable() { return msg('pokedexUnavailable'); },
  get noMoveSelected() { return msg('noMoveSelected'); },
  get selectMove() { return msg('selectMove'); },
  get noMovesRecorded() { return msg('noMovesRecorded'); },
  get movesWillAppear() { return msg('movesWillAppear'); },
  get moveEffectUnavailable() { return msg('moveEffectUnavailable'); },
  get abilityUnavailable() { return msg('abilityUnavailable'); },
  get moveDataUnavailable() { return msg('moveDataUnavailable'); },
  get chooseMoveList() { return msg('chooseMoveList'); },
  get noAdditionalData() { return msg('noAdditionalData'); },
} as const;
