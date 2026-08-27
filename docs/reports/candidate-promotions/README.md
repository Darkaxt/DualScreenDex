# Candidate promotion validation evidence

This directory retains the bounded public evidence used only by the authorized `automated-passive-catalog` candidate-promotion mode. One JSON document belongs to one signed candidate. It must contain no ROM bytes, filesystem paths, credentials, device identifiers, or personal details.

The promotion workflow verifies the file's SHA-256 from the canonical promotion record, then requires this schema:

```json
{
  "schema": 1,
  "candidateTag": "v1.1.0-rc.73",
  "apkSha256": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "sourceCommit": "ffffffffffffffffffffffffffffffffffffffff",
  "validationMode": "automated-passive-catalog",
  "automatedValidationWorkflowRunUrl": "https://github.com/Darkaxt/DualScreenDex/actions/runs/789",
  "userAuthorizedAutomatedPromotion": true,
  "gameplayRuntimeChanged": false,
  "catalogPersistenceValidated": true,
  "runtimeApiValidated": true,
  "webPresentationValidated": true,
  "releaseCiValidated": true,
  "controls": [
    {
      "romSha256": "1111111111111111111111111111111111111111111111111111111111111111",
      "catalogPersistenceValidated": true,
      "runtimeApiValidated": true,
      "webPresentationValidated": true
    },
    {
      "romSha256": "2222222222222222222222222222222222222222222222222222222222222222",
      "catalogPersistenceValidated": true,
      "runtimeApiValidated": true,
      "webPresentationValidated": true
    },
    {
      "romSha256": "3333333333333333333333333333333333333333333333333333333333333333",
      "catalogPersistenceValidated": true,
      "runtimeApiValidated": true,
      "webPresentationValidated": true
    },
    {
      "romSha256": "4444444444444444444444444444444444444444444444444444444444444444",
      "catalogPersistenceValidated": true,
      "runtimeApiValidated": true,
      "webPresentationValidated": true
    },
    {
      "romSha256": "5555555555555555555555555555555555555555555555555555555555555555",
      "catalogPersistenceValidated": true,
      "runtimeApiValidated": true,
      "webPresentationValidated": true
    }
  ]
}
```

Replace every sample identity and URL with evidence for the candidate. At least five distinct exact ROM SHA-256 identities are required; every control must pass catalog reopen, runtime API projection, and web presentation. The cited run must be a successful `.github/workflows/ci.yml` execution at `sourceCommit`.
