# Stage 4 Gen III nature-name authority checkpoint

Date: 2026-09-11

Status: **false name-table authority corrected; final matrix capture must be regenerated**

## Finding

Independent G4 field review rejected two otherwise selected controls. Italian Emerald published contest/menu labels such as `CLASSE` and `BEL.ZA` as `NATURE_NAMES`; Japanese Emerald published labels such as `かっこよさ` and `うつくしさ`. Both controls reported `AVAILABLE 25/25`, so persistence and API parity alone would have preserved semantically incorrect text.

The other twelve official Gen III controls exposed the standard Nature domain. No policy disposition changed: all fourteen Gen III controls still require `NATURE_NAMES` to be accepted, while Gen I and Gen II remain not applicable.

## Root cause and correction

The separate-table Nature resolver proved the numeric stat and flavor tables through compiled consumers, but selected a name-pointer table independently by highest reference count. Its former plausibility gate accepted any mostly distinct 25-label table. In the affected Emerald localizations, a more frequently referenced 25-entry menu table therefore outranked the actual Nature names.

Name nomination now requires all 25 labels to be distinct and three language-semantic boundary markers at Nature IDs 0, 1 and 24. The markers are language-level Gen III semantics—never ROM identities, hashes, titles, offsets or routing profiles. English, French, German, Italian, Spanish and Japanese are covered. A modified or unratified label domain fails closed for localized names while the separately proved numeric Nature mechanics remain available.

## Verification boundary

A synthetic regression was observed RED before the new semantic-authority function existed. Requiring distinct labels alone was then disproved by the exact Japanese control, whose incorrect menu table contains 25 distinct labels. The final focused gate requires:

- an unrelated 25-label table to fail name authority even when every label is distinct;
- a standard boundary-marker table to pass;
- exact Italian Emerald output to begin `ARDITA`, `SCHIVA` and finish `FURBA` in the complete standard order;
- exact Japanese Emerald output to begin `がんばりや`, `さみしがり` and finish `きまぐれ` in the complete standard order.

The focused synthetic plus two-control gate passed two tests with zero failures/errors/skips. The complete parser/cache checkpoint then passed **2,169 tests** with zero failures/errors (**2,054 parser-core, 115 catalog-store; 206 environment-inapplicable tests skipped**). Parser revision **81** invalidates revision-80 caches that may contain the false Nature-name domain; storage schema remains **2** and codec version remains **1**.

The source-bound G0–G4 artifacts generated from parser revision 80 are now diagnostic only. The parser CLI, both partitions, caches, receipts and every downstream binding must be regenerated after this correction is committed. This checkpoint accepts no final control and does not weaken the fixed 506 accepted / 154 excluded capability policy.
