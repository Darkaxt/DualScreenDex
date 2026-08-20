# DualDex 1.1.0-rc.25

RC25 expands source-backed Generation I move and machine parsing without changing unrelated catalogs or navigation.

## Generation I parser

- Compiled move-name and six-byte move-data consumers now resolve expanded move domains generically from raw ROM structure.
- Dependent learnsets validate against the resolved move domain instead of the inherited 165-move limit.
- Compiled TM/HM search and lookup consumers now resolve machine lists and species compatibility flags without a global byte-pattern guess.
- Ambiguous or malformed optional tables fail closed while the rest of the catalog remains available.
- The 95-ROM Gen I audit completed with 82 selected, zero ambiguous, 13 explicit no-family matches, zero parser errors, and exact SQLite reopen parity for every selected catalog.

## Delivery

- RC25 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
