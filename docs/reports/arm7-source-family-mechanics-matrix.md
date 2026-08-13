# ARM7 Source-Family Ability Mechanics Matrix

This matrix is the source oracle for the isolated ARM7 mechanics core. It separates compiler/code-shape variance from runtime ABI variance. Source paths, commits, ROM hashes, symbols, and absolute addresses are test provenance only; production eligibility may not consume them.

## Authorities

| Control | Local authority | Commit | Engine-family independence |
|---|---|---:|---|
| Pokémon Emerald | `D:\Temp\pokeemerald-source` | `9a83a2bbe8e097e62c00f1dbd56849766775d7b6` | Pret Emerald retail family |
| Pokémon FireRed / LeafGreen | `D:\Temp\pret-pokefirered-source` | `c75f352304d529f6ba92d4f74b9cf8b5c3810788` | One FRLG retail family; the two ROMs corroborate but do not count twice |
| Modern Emerald 3.5 | `D:\Temp\dualscreendex-modern-emerald-3.5-source` | `01a4212b9718886b19ce6e45c332ba618cc92a26` | Recompiled/expanded Modern family |
| Pokémon Classic | `D:\Temp\pokeclassic-source` | `c85ebde792bd8e40d3e9cb5aa805af3bd701f606` | Expansion-style battle engine and distinct ABI |

## Axis A: code shape and factoring

| Family | Ability-bearing routine | Caller/role contract | Arithmetic and compiler shape |
|---|---|---|---|
| Pret Emerald | `CalculateBaseDamage(BattlePokemon *attacker, BattlePokemon *defender, move, ...)` at `src/pokemon.c:3106` | The first two arguments are direct attacker/defender battle-record pointers. Seven source callsites pass records derived from `gBattleMons`. The routine directly loads the selected move row. | Inline `attack *= 2` and `(150 * attack) / 100`; Thumb compiler factoring includes shared blocks and division helpers. |
| FRLG | `CalculateBaseDamage(...)` at `src/pokemon.c:2385` | Same pointer-role contract; six source callsites. | Retail semantics equivalent to Emerald, but independently linked/rearranged Thumb code and helpers. |
| Modern | `CalculateBaseDamage(...)` at `src/pokemon.c:6349` | Same pointer-role contract and seven source callsites. It also reads explicit move category and extra global state. | Recompiled, much larger routine with different CFG, register allocation, inlining and helper factoring. Inline `x2` and `(150*x)/100` remain source semantics, not valid byte signatures. |
| Classic | `CalcAttackStat(move, battlerAtk, battlerDef, moveType, isCrit, updateFlags)` at `src/battle_util.c:8611`, called by the damage calculation at `:9146` | Arguments are battler indices rather than record pointers. The routine derives `gBattleMons + index*stride` and calls `GetBattlerAbility`; role proof must follow that dataflow. | Ability dispatch is a switch. It accumulates unsigned Q4.12 modifiers through `MulModifier` and applies them with `ApplyModifier` (`:7946-7953`, `:8806`). No `CalculateBaseDamage`/inline-ratio assumption applies. |

## Axis B: typed runtime layouts

| Family | `BattlePokemon` fields required by this gate | Move-record ABI required by this gate |
|---|---|---|
| Pret Emerald | stride `0x58`; Attack `u16@0x02`; Defense `u16@0x04`; SpAttack `u16@0x08`; SpDefense `u16@0x0A`; Ability `u8@0x20`; HP `u16@0x28`; MaxHP `u16@0x2C`; Status `u32@0x4C`. Source: `include/pokemon.h:260-295`. | Compiler stride 12 (`RETAIL_12`); effect `u8@0`, power `u8@1`, type `u8@2`, target `u8@6`, priority `s8@7`, flags `u8@8`. Source declaration: `include/pokemon.h:327-338`. Physical/special is Gen III type-derived. |
| FRLG | Same retail battle-record ABI. Source: `include/pokemon.h:170-206`. | Same retail move ABI. Source: `include/pokemon.h:238-249`. |
| Modern | Same `0x58` battle-record offsets and widths. Source: `include/pokemon.h:270-306`. | Compiler stride 12; retail prefix plus category `u8@9`. Source: `include/pokemon.h:348-364`. The parser must expose the selected category field instead of inferring it from a ROM identity. |
| Classic | Shipped-ROM consumers prove stride `0x5C`, attack `u16@0x02`, ability `u16@0x20`, and status `u32@0x50`. HP/maxHP are deliberately absent from this mechanics ABI. | `BATTLE_ENGINE_20`; effect `u16@0`, power `u16@2`, type `u8@4`, target `u16@8`, priority `s8@10`, flags `u32@12`, split `u8@16`, argument `u8@17`, Z fields `u8@18/19`. Source: `include/pokemon.h:232-247`. |

The production analyzer must accept these values only through a validated `BattleMechanicsAbi`. It must not contain a retail/Modern/Classic branch, default field offsets, or an ABI selected from a ROM label, hash, source commit, symbol, or function address.

### Classic shipped-ROM ABI corroboration

The exact shipped Classic control (`01c0177b...`) agrees with the exact source in independent consumers; the addresses below are test/diagnostic evidence only and are not production selectors:

- `GetBattlerAbility` source semantics (`src/battle_util.c`) correspond to the routine entered at ROM `0x4AB6C`. Its input-battler path at `0x4AB8C-0x4AB94` computes `index * 0x5C + 0x020230F8` and performs `ldrh` at `+0x20`. Its active-battler path independently repeats the same stride/root/halfword field at `0x4AB9E-0x4ABAA`.
- The Mummy/Wandering Spirit source cases (`src/battle_util.c:5146-5200`) correspond to the independent compiled consumer at `0x48C9C-0x48D18` and swap path at `0x48DA2-0x48E60`. They repeatedly compute `index * 0x5C + 0x020230F8`, load the ability with `ldrh +0x20`, and store it with `strh +0x20`.
- `CalcAttackStat` (`src/battle_util.c:8611-8806`) begins at ROM `0x5097C`. Its normal physical path derives the attacker record from parameter `r1` using `index * 0x5C + 0x020230F8`, loads `ldrh +0x02` at `0x50A10`, and carries that value through stat-stage arithmetic at `0x50A32-0x50A48` before the ability modifier dispatch.
- `GetSplitBasedOnStats` (`src/battle_util.c:9763-9778`) independently begins at `0x52978`; `0x52984-0x529C4` uses the same root and `0x5C` stride, loads attack via `ldrh +0x02`, and feeds it through the stat-stage ratio before comparing it with special attack.
- The Guts case in `CalcAttackStat` derives `index * 0x5C + 0x020230F8 + 0x50` and reads its low byte at `0x50CAE`; this implements the source's `STATUS1_ANY` low-byte mask. An independent compiled loop at `0x3BFB8-0x3BFD4` derives the same root, stride, and offset and performs a full-word load. Additional full-word consumers at `0x168480-0x168492`, `0x1685E0-0x1685F0`, and `0x169822-0x169830` corroborate the field width.

This admits stride, attack, ability, and status. Source member comments after the widened ability field remain semantic labels rather than binary layout proof. HP/maxHP stay out of scope.

## Exact target tuples

Canonical target form is `(abilityId, target, predicates, effect)`. `ATTACKER_ABILITY(id)` always means a value proven to originate from the typed attacker ability field or the typed Classic battler-role ability accessor.

| Family | Ability | Exact source-supported tuple |
|---|---:|---|
| Pret Emerald / FRLG / Modern | 37 Huge Power | `(37, ATTACKER.ATTACK, [ATTACKER_ABILITY(37)], MULTIPLY(2,1))` |
| Pret Emerald / FRLG / Modern | 74 Pure Power | `(74, ATTACKER.ATTACK, [ATTACKER_ABILITY(74)], MULTIPLY(2,1))` |
| Pret Emerald / FRLG / Modern | 55 Hustle | `(55, ATTACKER.ATTACK, [ATTACKER_ABILITY(55)], MULTIPLY(3,2))` |
| Pret Emerald / FRLG / Modern | 62 Guts | `(62, ATTACKER.ATTACK, [ATTACKER_ABILITY(62), ATTACKER_STATUS_PRESENT], MULTIPLY(3,2))` |
| Classic | 37 Huge Power | `(37, ATTACKER.ATTACK, [ATTACKER_ABILITY(37), MOVE_SPLIT(PHYSICAL)], MULTIPLY(2,1))` |
| Classic | 74 Pure Power | `(74, ATTACKER.ATTACK, [ATTACKER_ABILITY(74), MOVE_SPLIT(PHYSICAL)], MULTIPLY(2,1))` |
| Classic | 55 Hustle | `(55, ATTACKER.ATTACK, [ATTACKER_ABILITY(55), MOVE_SPLIT(PHYSICAL)], MULTIPLY(3,2))` |
| Classic | 62 Guts | `(62, ATTACKER.ATTACK, [ATTACKER_ABILITY(62), ATTACKER_STATUS_NONZERO(mask=0xFF), MOVE_SPLIT(PHYSICAL)], MULTIPLY(3,2))` |

Retail source evidence is `pokeemerald/src/pokemon.c:3158-3159,3205-3212` and `pokefirered/src/pokemon.c:2437-2438,2482-2489`. Modern evidence is `src/pokemon.c:6403-6404,6604-6605,6746-6747`. Classic evidence is `src/battle_util.c:8665-8672,8729-8740`.

## Proof stages required per tuple

1. **Routine located:** a complete decoded ARM/Thumb CFG begins at a real decoded call target and has bounded, valid terminal returns.
2. **Role proven:** caller/callee dataflow establishes attacker/defender pointers or Classic battler-index-to-record derivation using the supplied stride.
3. **Ability dependency proven:** the compared/switch value originates from the typed ability field/accessor and remains within the selected ability domain.
4. **Predicate dependency proven:** status or move split originates from its exact typed field/table row when required.
5. **Effect arithmetic proven:** IR/dataflow or bounded execution establishes the exact rational multiplier, irrespective of inline, division-helper, or Q4.12 representation.
6. **Writeback proven:** the transformed value reaches the attack-stat result used by the caller.
7. **Counterfactual proven:** changing one semantic input suppresses only its dependent mechanic; mutation of each comparison/predicate/transform/writeback dependency removes that mechanic.

An unsupported dependency fails only that mechanic. It must not convert other independently proven mechanics into a family-wide failure.

## Anti-overfit gate

- Tests may use source symbols and addresses to explain failures, but the resolver API receives only ROM bytes, selected typed layouts, typed ABI, and semantic domains.
- Candidate eligibility may not originate from raw multiplication/division opcodes, English names, nearby table references, nearest prologue, a minimum mechanic count, or a known ROM fingerprint.
- FireRed and LeafGreen count as one engine family. Modern and Classic must independently pass before held-out hacks are measured.
- Every control asserts the exact tuple set and rejects extras. Two fresh analyses must serialize identically.
