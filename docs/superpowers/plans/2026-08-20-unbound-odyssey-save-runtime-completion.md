# Unbound and Odyssey save/runtime completion plan

> Execute continuously through a separate save-stage commit. Do not count erased flash or unresolved fields as support.

## Exact evidence

- Odyssey v4.1.1 ROM SHA-256 `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0`; populated 128 KiB save SHA-256 `645282db3d0f6e5723930cc35793a39f2044f96cf1d658f139f04af5482fdcf3`.
- Unbound v2.1.1.1 ROM SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
- All three supplied Unbound 128 KiB saves are the same erased-flash image: SHA-256 `b5a41c3758763bbec72769fab4a2533bf2db0b6312d93d25a695f9e4b9e02260`, with every byte equal to `FF`.

## Task 1: Freeze the real baseline

- Add an exact ROM-to-runtime-to-save control that uses the catalog-published save ABI.
- Require Odyssey's existing 12/14 domains and exact Trainer/Bag failures before production edits.
- Require Unbound's erased save to remain unsupported; it must never be reported as an empty but valid slot.

## Task 2: Complete Odyssey Trainer and Bag

- Derive SaveBlock1/SaveBlock2 pointers and field roles from decoded ROM consumers.
- Publish a typed save ABI only when block sizes, Trainer fields, encryption key, money, badges, and all five Bag pockets agree with the populated checksum-valid save.
- Require 14/14 capabilities, deterministic decoded aggregates, and runtime/API projection. Do not select by ROM identity or fixed linked addresses.

## Task 3: Implement the Unbound expanded-save format

- Use the CFRU/Unbound source as a semantic oracle for 0xFF0-byte data sectors, 128-byte footer, parasite fragments, sections 30/31, signature validation, and per-section checksum lengths.
- Recover the corresponding directory/signature/layout authority from the exact ROM rather than embedding Unbound offsets or identity rules.
- Add corrupted-sector and incomplete-slot fail-closed controls around the decoder.
- Keep the exact erased save unsupported. A populated exact save is required before claiming any of the 14 runtime capabilities.

## Task 4: Persist, report, and commit

- Verify Odyssey twice through CatalogParser, runtime SaveParseContext, SaveParser, and public state projection.
- Verify Unbound structural ABI selection and erased-flash rejection.
- Run focused parser/save/runtime tests, `git diff --check`, and a production identity-selector scan.
- Report numeric before/after results and the Unbound fixture blocker; commit the save stage separately.
