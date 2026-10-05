# Gen III widened compiled-core recovery

**Status: IN_PROGRESS — final affected parser/catalog guards passed (276 passed, one optional original-ROM test skipped); committed-source real-input acceptance pending.**

## Scope and authority

Recover canonical species/name/stat/type and reference-safe level-up catalogs for the retained ROWE Experimental 2.1.9.1 and 2.1.1 builds. Use generic compiled consumers, never project titles, hashes, fixed roots, presumed retail indices or source cardinalities. Preserve related Gen III controls. Maps, live/save memory, numeric ability mechanics, Android/device/emulator work, signing and releases are outside this batch.

The initial public source snapshot is materially older than these binaries. The newer [RoweRepo structural oracle](https://github.com/BelialClover/RoweRepo/tree/e596e740cffbfb1d18e9bbd1ee2bd6a757f478c8) declares widened stats and names. Neither source version is exact-build authority. Each original diagnostic admission was reviewed separately; consumed admissions cannot authorize parser executions or retries. Public acceptance must omit original identities, private paths and decoded ROM data.

## Established compiled contracts

Both inputs independently expose:

- A complete bounded species-name copier: unsigned species input, multiplication by the stored name width, a ROM root, explicit maximum species index, dummy-row fallback, byte-copy loop, EOS stop and bounded return.
- Paired forward/inverse species-to-Dex consumers: the same u16 map root, explicit inverse scan bound and not-found sentinel, `(species - 1) * 2` forward indexing, and the optional form-pointer first-u16 path. The name bound and map bounds agree, including their separate reserved slots.
- A complete default/variant type predicate: a cleared flag selects one stat root and a set flag selects another; both index by 64 and read type bytes at 12/13. This authorizes the static unflagged catalog, not current runtime mode detection.
- Independent six-field widened stat reads at offsets 0, 2, 4, 6, 8 and 10, coupled to the same indexed default root.
- Four-byte level-up list traversal: pointer indexing by species, move u16 at 0, level u16 at 2, stride 4, move-end sentinel, and ordinary unshifted level comparisons. A separate evolution-learning caller compares against a shifted runtime level; it is not authority to reinterpret the stored level ABI or claim corrected game mechanics.

The exact map/form diagnosis finds 1,025 canonical native IDs in each build, with complete name/stat/type joins and no canonical forward/inverse conflict. These counts are observations of the compiled contracts, not implementation constants. Every canonical list terminates and every stored level is in 0–100. Experimental's canonical lists reference move IDs through 851; 2.1.1's through 832. A coherent move domain must therefore be recovered before publishing level-up links; the older 339-move catalogs are insufficient.

## Widened stat codec

Add a distinctly named 64-byte ABI to the existing typed codec. It remains the sole byte interpreter for this format; do not add ad hoc materializer decoding or include it in ordinary retail structural stride guessing.

| Field | Offset / width |
|---|---|
| HP, Attack, Defense, Speed, Special Attack, Special Defense | 0, 2, 4, 6, 8, 10 / u16 |
| Types | 12, 13 / u8 |
| Catch rate | 14 / u8 |
| Experience yield | 16 / u16 |
| Packed EV yield | 18 / u16 |
| Held items | 20, 22 / u16 |
| Gender, egg cycles, friendship, growth | 24, 25, 26, 27 / u8 |
| Egg groups | 28, 29 / u8 |
| Four ability slots | 30, 32, 34, 36 / u16 |
| Safari flee, packed body/no-flip | 38, 39 / u8 |

The remaining engine-specific fields are not interpreted as mechanics. Exact zero rows remain structural empty; nonzero malformed records are not silently excluded. Preserve cancellation, long arithmetic and deterministic extent limits. Keep retail 28-byte and battle-engine 32-byte results unchanged.

## Canonical domain and proposal resolution

A focused compiled-core resolver owns consumer recognition and root/count coupling. It must distinguish absent contracts from recognized incomplete, conflicting, malformed or budget-limited contracts. A recognized rejection must not fall back to inherited wrong-stride roots.

The inverse map supplies canonical native rows by its first-match semantics; the forward map/form path must agree on every canonical join. Do not infer National Dex numbers from row order or name spelling. Native-index and Dex identity are separate test cases. The domain is independent of stat plausibility and name-prefix length. Alternate/form rows and runtime variants are not promoted into canonical records; any remaining form capability needs its own ledger closure.

Names are decoded only under the proven copier geometry and canonical row authority. Stat proposals carry their ABI and independently proven domain into the typed codec/resolver. Complete active coverage, references and unambiguous roots are required. Integrate authority consistently across family probing, semantic-domain evaluation and materialization; no later legacy path may overwrite the proven stride or index map.

## Level-up and move prerequisites

Reuse the existing typed u16-move/u16-level decoder only after admitting the complete ordinary level consumer. Validate pointer bounds, termination, level values, list budgets and all move/species references. A runtime-only evolution caller does not redefine static level data.

Recover the compiled 17-byte move-name root and each independently compiled move-detail stride. Retained ordinary acquisition consumers index 20-byte records in 2.1.1 and 56-byte records in Experimental; both independently expose effect u16 at 0, byte fields at 2–7, signed priority at 8, flags u32 at 12, split at 16 and argument at 17. The newer public oracle declares the common aligned prefix but does not explain Experimental's full stride. Neither matches the existing hybrid decoder's widened target at 8 and priority at 10. Require the actual field consumers and distinct typed ABIs; never promote the remaining 56-byte extension as mechanics. Typed codecs remain the sole byte interpreters. A domain established by complete canonical acquisition references is a proven ordinary minimum, not proof of every possible game move; report this distinction if no independent full move bound is available. Do not publish dangling links, guessed counts or a guessed category/flag ABI. If a prerequisite remains unproven, preserve fail-closed status and explicitly ledger the named gap rather than counting relationship recovery.

## Verification and publication gates

1. Observed RED/GREEN synthetic codec and compiled-consumer tests: widened values, all four ability slots, relocation, unsigned inputs, nonidentity/reordered indices, zero/dummy/form slots, default/variant inversion, conflicting roots/counts, truncated pointers, malformed rows, EOS, list bounds, cancellation and deterministic budgets.
2. Focused family/materializer/reference tests and cache invalidation when production parser behavior changes. Parser cache revision advances from 84 to 85; SQLite's SQL version remains 2 for this parser-only revision.
3. Smart-sync with fork/master immediately before each coherent source/acceptance commit. Never overwrite unrelated changes or force-push.
4. Rebuild the packaged CLI with the exact committed source stamp. Review a fresh MAIN admission for both targets and justified Gen III controls; no full-library rerun or reuse of diagnostic admissions.
5. Verify exact inputs, runtime/report receipts, complete catalog references, full SQLite write/reopen structural equality and matching logical digests. Host tests alone are not real-input acceptance.
6. Publish only actual gains and field-level limits under LIB26-G3-CORE. Update only accepted matrix rows; preserve unrelated rows and their historical provenance. No APK/release is implied.
7. Retain compact private proof, then inspect and apply only the exact ticketed disposable scratch cleanup. Preserve all retained corpus, source and original archive paths.

Every Windows Gradle invocation uses the shared host gate, two workers, no parallel projects, initial 3 GB Gradle/Kotlin heaps and bounded test forks. Report the acquired gate and actual observed profile. No concurrent agents, workflows or device work.
