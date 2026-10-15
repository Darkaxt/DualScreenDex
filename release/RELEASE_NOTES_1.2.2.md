# DualDex 1.2.2

## Fixed

- Fixed Android folder-picker game scans failing or finding no games when opening uncached ROMs. The scanner could wait on a ROM read queued behind its own directory-listing operation. Directory listings now finish and close before individual ROMs are opened, while retaining traversal limits, cancellation and previous-index safeguards.
- This addresses [#36](https://github.com/Darkaxt/DualScreenDex/issues/36), reported with Spanish FireRed on AYN Thor: manual Pokédex loading worked, but scanning could fail and leave RetroArch content unresolved. The correction is generic, not specific to a ROM, language or device.

After updating, select the game folder and run **Scan games again**. Once indexing succeeds, normal RetroArch content and save matching can proceed with the existing configuration. This does not change RetroArch's network, save-directory or autosave requirements.

## Verification and scope

The focused same-provider regression reproduced the timeout before the correction. Storage host checks passed with 42 passing tests and two unconfigured original-ROM controls skipped. The candidate's GitHub managed Android suite passed all 11 tests, including three generated-only regressions exercising the real Android indexer/provider path: uncached GBA/ZIP scans from exact/nested folders, cached rescans and quota exhaustion before callbacks. CI, browser acceptance and protected signing passed. No reproduction on the reporter's hardware or local device acceptance is claimed.

This is a focused hotfix on the 1.2.1 release baseline. ROM parsing, gameplay decoding, API/web production modules and catalog formats are unchanged; unfinished parser work is not included. Existing parser compatibility and five original-ROM controls are explicitly inherited for exact unchanged production trees, not presented as a newly executed ROM corpus. See the [source-bound candidate evidence](https://github.com/Darkaxt/DualScreenDex/blob/v1.2.2/docs/reports/candidate-promotions/v1.2.2-rc.1.json).

Release packages use DualDex's existing production application identity and GitHub-managed signing certificate.
