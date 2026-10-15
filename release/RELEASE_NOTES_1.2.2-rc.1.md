# DualDex 1.2.2-rc.1

## Fixed

- Fixed Android folder-picker game scans failing or finding no games when opening uncached ROMs. The scanner could wait on a ROM read queued behind its own directory-listing operation. Directory listings now finish and close before individual ROMs are opened, while retaining traversal limits, cancellation and previous-index safeguards.
- This addresses [#36](https://github.com/Darkaxt/DualScreenDex/issues/36), reported with Spanish FireRed on AYN Thor: manual Pokédex loading worked, but scanning could fail and leave RetroArch content unresolved. The correction is generic, not specific to a ROM, language or device.

After updating, select the game folder and run **Scan games again**. Once indexing succeeds, normal RetroArch content and save matching can proceed with the existing configuration. This does not change RetroArch's network, save-directory or autosave requirements.

## Scope

This is a focused hotfix on the 1.2.1 release baseline. ROM parsing and catalog formats are unchanged; unfinished parser work is not included. Existing parser compatibility evidence is retained rather than presented as a newly executed ROM corpus. Generated-only Android provider regressions cover uncached GBA/ZIP scans, cached rescans and traversal limits; no reproduction on the reporter's hardware is claimed.

Release packages use DualDex's existing production application identity and GitHub-managed signing certificate.
