# Unbound follow-up qualification — October18

Status: parser fix and bounded output comparison complete; protected candidate signing/publication and final release acceptance remain pending.

## What was qualified

Parser source `d24b7e325346fbc978c570476979514ca21a0a85`, parser cache140, SQLite2, generator schema16. The isolated13-jar runtime digest is `aa272f07ea9936e665c100ecc4ed0befe4f2ea94c73e6528c7c5210fad1f6814`.

One separately MAIN-reviewed batch evaluated **five exact affected/control inputs**, serially with3GiB, two original hash/stat passes and unchanged originals. It produced five persisted/reopened catalogs with no parser, catalog or persistence errors. It did not copy originals, run a game or access a device. Raw report SHA256: `df6f662224908c8d2ca4c907f624ff9fb7e5ad57587a2b539d5a39ef8a1552e7`; execution receipt SHA256: `4d38374389e3d9bf6b7aa5e41598efb93484889401b0045095a53a476d6ef80d`.

| Affected/control input | Prior → current maps | Saved-output result |
|---|---:|---|
| Unbound2.1.1.1 |444→458|All14 required gains; all444 prior maps pixel-exact; all2,803 prior POIs and12 scenes exact;77 additional POIs belong only to restored maps.|
| Liquid Crystal3.3.00512 |185→193|All185 prior maps pixel-exact;108 added POIs; four unsupported-SMOL map failures and the separate invalid event-pointer diagnostic remain fail-closed.|
| Emerald Ex1.0.4 |431→432|All431 prior maps pixel-exact;10 added POIs; existing six scenes unchanged.|
| Modern Emerald3.5 |557→557|Whole local-map payload, timed assets and all other persisted sections unchanged, apart from the new empty default tiled store.|
| Odyssey4.1.1 |110→110|Whole local-map payload and all other persisted sections unchanged, apart from the new empty default tiled store.|

Independent generated-only audit verifies89 saved sections,279,676 typed/localized references, exact write/reopen logical digests and all prior static-map pixels. All16 surfaced comparison candidates were individually reconciled: restored-map storage/POI/name/coverage gains and the existing CLI's empty relative source for single-file roots. Display names and admitted input identities remain bound. There are zero unresolved candidates or integrity/reference/map-loss/prior-pixel errors. Species, moves, abilities, acquisitions, encounters, runtime metadata, world maps and themes remain exact.

Audit SHA256: `6bd8b6a6351680f2623369b246f32c2b53e1502b84e051d48ee051010f06919b`. Adjudication SHA256: `3853e170134c881c0893d2b7bc62b8494e1e9d6cba96dda4db51eb44cf7b6207`. Compact private proofs and their source/runtime/producer bindings are retained; no originals or private paths are published.

## Integration and rendering

The focused generated integration run passes51 tests, zero failures/errors/skips, covering parser budgets, strict tiled validation, cancellation, crops/alpha, lazy deterministic theme selection, SQLite reopen, revision139 rejection and shared PNG/API availability. Actual old-code RED reproduces12 omitted maps in a64-map generated catalog. Existing pixel/encoded/per-map limits, encounter/cardinal priority and graph semantics are unchanged.

A separate retained-output test exercises the actual Kotlin codec/renderers against all313 prior Unbound PNG assets:99,988,480 exact pixels and1,252 exact crops. The representation uses6,067,456 stored pixels and1,909,217 compressed bytes. Receipt SHA256: `de9ee818e0dae6c55353979a3d8d82f3e47fcddce2e8dd24408b33f5cd2ae885`.

## Baseline reuse, not another full corpus

The completed released139 full342 evidence remains attributed to its original `36d6c2d4ee690e9069cc0af6d1ddb48f9ad4fc89` source/runtime. Its canonical342 digest is `733e36785fa9a5b71bc4121343a6b6d2cbd27e43e08b3fddc2e2870bac1a0561`; report SHA256 is `f5a00ec3f4c7381623a2056724bc0220311d2320ed2c006057004e1f36eb4d50`.

An unnecessary follow-up full342 attempt was cancelled at53 completed inputs after the user's correction. Its consumed authority, logs and partial output are preserved as cancelled, not successful acceptance or reusable execution authority. No replacement full-corpus run is required or scheduled.

`BOUNDED_REQUALIFICATION` in the release manifest keeps the old full baseline and the fresh five-input summary/receipt separate. Every changed evidence-affecting source file is pinned to the qualified commit and checked against release bytes; unreviewed or later parser/build/generator changes fail closed. Existing cache, source-lineage, privacy, QA-closure, signing and promotion checks remain in force.

These are static parser/catalog checks, not proof of live gameplay, native behavior or physical-device acceptance. New-map pixels derive from the admitted static parser rather than an independent original-ROM decoder. All five inputs remain partial in other optional domains. No all-ROM100% completion is claimed.
