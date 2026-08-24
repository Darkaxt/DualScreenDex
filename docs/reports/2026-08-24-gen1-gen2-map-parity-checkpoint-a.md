# Gen I/II Map Parity Checkpoint A Corpus Evidence

Specification: `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

## Complete GB/GBC matrix

The evidence runner rehashed every manifest entry, selected raw-header GB/GBC candidates, parsed each selected Gen I/II ROM twice from fresh bytes, and emitted no filenames, private paths, archive entries, or ROM bytes. The pre-stage baseline was commit `d6b3722`; the complete current matrix used parser commit `8cf81da`.

Before this evidence was committed, the branch was rebased onto master `2ed446c`. Incoming changes affected unified runtime state, Gen III runtime descriptors, and API trainer-state precedence, but not Gen I/II parser selection or Local-map resolution. The focused strict scene controls, `ApiViewBuilderTest`, and `BattleMemoryCoordinatorTest` all passed on the integrated tree.

| Result | Pre-stage | Current |
|---|---:|---:|
| Manifest hashes verified | 334 / 334 | 334 / 334 |
| Raw-header GB/GBC candidates | 128 | 128 |
| Parser-selected Gen I/II rows | 102 | 102 |
| Deterministic rows | 102 / 102 | 102 / 102 |
| Parser errors | 0 | 0 |
| Local-map rows available | 57 | 57 |
| Accepted Local maps | 13,685 | 13,685 |
| Accepted raster assets | 13,685 | 13,685 |
| Rows with scenes | 0 | 57 |
| Total scenes | 0 | 69 |

All 102 rows retained the same manifest identity, parser generation/family, Local capability, map count, static/indexed/timed asset counts, raster signature, and error state. There were zero accepted-raster regressions. Every one of the 57 available Local catalogs gained at least one bounded scene.

The remaining 45 selected rows still report `LOCAL_MAP NOT_FOUND`, exactly as they did before this checkpoint. Forty-four fail closed at map-authority resolution and one at encounter binding; none reports a parser error or partial scene. Atlas therefore remains available without suppressing unrelated capabilities.

## Source-backed strict scene controls

Public Shin source revision `a7a9b1361e55aaa5afed6b5d14b5e7bd44002179` defines Pallet Town with a north connection to Route 1 and a south connection to Route 21. Three corresponding compiled Red/Blue/Green release controls independently retained 226 maps, one exact scene signature, and the compiled Pallet Town (`0x00`) to Route 1 (`0x0c`) displacement `(0, -36)`.

A focused rerun rehashed all three inputs, parsed each twice from fresh bytes, compared all three to the pre-stage raster baseline, and reported:

```text
selected=3
deterministic=3
parserErrors=0
maps=678
assets=678
scenes=3
baselineCompared=3
baselineRegressions=0
strictControls=3
strictControlFailures=0
```

The source snapshot predates the tested release controls by one day and agrees with the compiled cardinal relation. No byte-identical source rebuild is claimed because RGBDS was unavailable locally; compiled ROM structure remains the acceptance authority.

## Classified compatibility gaps

Task #153 tracks all 45 pre-existing Local-map authority gaps. The 40 rows with potential local public-source oracles are prioritized: Celebrations, Beyond, Red++, Static Yellow, matching Kaizo Christmas controls, Anniversary Crystal, Crystal Legacy and Timeless, Gold/Silver 97 Reforged, Mystic Crystal, Orange, and Peridot. Grape, Dark Energy, two Crystal Clear variants, and Crystal Kaizo remain deprioritized until matching public source is available.

This is a valid Checkpoint A deferral rather than a regression: these rows had no accepted Local raster before scene work, retain bounded `NOT_FOUND` diagnostics, and continue to use the specified Atlas fallback. Task #153 closes only when source-matched compiled families resolve generic Local catalogs without production identities, preserve existing capabilities and raster controls, and pass focused plus corpus validation. Corpus-wide Gen I/II Local support is not claimed until that task closes.

## Outcome

The Checkpoint A corpus gate passes: all hashes were verified, every selected row was deterministic, parser errors and accepted-raster regressions were zero, accepted Local maps retained exact raster output, and the source-backed scene controls matched their compiled geometry.
