# DualDex 1.2.4 — Unbound map coverage

Restores the 14 budget-omitted Unbound maps: **458/458 reachable static maps**, preserving all 444 previously retained maps pixel-for-pixel and their existing POIs and scenes.

- Lossless, self-contained tile storage fits oversubscribed static maps within the existing resource limits.
- Liquid Crystal gains eight maps and Emerald Ex gains one. Independently unsupported Liquid Crystal formats remain explicitly unavailable.
- Under-budget maps and timed lighting keep their existing representation and behavior. No new UI or game-control feature is introduced.
- Parser cache revision140 rebuilds revision139 catalogs; SQLite schema2 is unchanged.

Qualification includes 51 focused integration tests, exact retained-map/crop comparisons and five freshly qualified affected/control inputs. The completed 1.2.3 full342 baseline is reused for unchanged scope; **this is not a new full-corpus run**. All five inputs still have unsupported optional domains; restored map coverage does not mean complete ROM/gameplay compatibility.

See [qualification details](../docs/reports/corpus/2026-10-18-unbound-followup-qualification.md). Production signing and publication use the protected GitHub release workflow. No physical-device acceptance is claimed.
