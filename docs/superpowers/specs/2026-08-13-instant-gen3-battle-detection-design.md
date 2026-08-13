# Instant Gen III Battle Detection Design

## Problem

RC23 observes the structurally resolved `Main.inBattle` flag quickly, but when the live battle layout is not cached it then reads all 256 KiB of EWRAM and 32 KiB of IWRAM in serialized 1 KiB Network Command chunks. The companion therefore publishes combat seconds after the opponent is already visible.

## Approved design

During ROM parsing, derive the `gBattleMons` EWRAM address from a unique structural cluster of compiled references to the battle-mon array and its related globals. The address is ROM-derived evidence; ROM names, hashes and absolute RAM addresses are not selection inputs.

Persist the optional address in catalog runtime metadata. When `Main.inBattle` becomes true, initialize the existing typed Gen III battle layout from that address and read only the bounded battle window. If the reference cluster is absent or ambiguous, retain the existing full-memory discovery path. Invalid live bytes remain fail-closed and cannot open combat mode.

## Success criteria

- Modern Emerald resolves the source-authoritative `gBattleMons` address from ROM code.
- A coordinator with parser-provided battle metadata proceeds from the small lifecycle read directly to the bounded battle-window read; it does not request full EWRAM or IWRAM.
- An absent or ambiguous address retains current discovery behavior.
- Existing battle validation, doubles handling, battle exit, live party detection, storage permissions and non-Gen-III behavior remain unchanged.
- RC24 is built and signed by the protected release workflow, installed in place, and verified without removing All Files access.
