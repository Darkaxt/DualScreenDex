# DualDex 1.1.0-rc.23

RC23 adds generic compiled-table discovery for Gen I evolutions, learnsets, and type matchups while retaining RC22's loading and save-integrity fixes.

## Gen I parser support

- Evolution and learnset roots now resolve from validated compiled pointer consumers instead of inherited fixed locations.
- Legacy type charts resolve from validated battle consumers, including expanded Gen I type IDs.
- Full-byte Gen I learnset levels are supported; Gen II retains its existing level bound.
- Optional table failures remain isolated: unsupported learnsets or type charts do not reject an otherwise valid catalog.
- Corpus coverage rises to 96.62% for evolutions, 89.47% for learnsets, and 95.79% for type charts across the 95-ROM Gen I denominator.

## Delivery

- RC23 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
