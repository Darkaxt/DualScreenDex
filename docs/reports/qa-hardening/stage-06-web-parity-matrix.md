# Stage 6 Android/Desktop Web Parity Matrix

This matrix is the canonical `WEB-09` contract. Android and desktop may use different transports, but their browser-visible HTTP behavior must match unless a row explicitly names a transport-specific requirement.

| Contract | Required result | Android owning evidence | Desktop/browser owning evidence |
| --- | --- | --- | --- |
| Health and bootstrap | `GET` returns `200`, JSON, and `Cache-Control: no-store` | `AndroidLoopbackServerTest.bindsOnlyToLoopbackAndServesPackagedUiAndCatalog` | `ServerContractTest.bindsOnlyToLoopbackAndServesHealth` |
| Current state | No/effectively older version returns `200` with current JSON state and `no-store` | `returnsNativeRuntimeChangesAfterTheClientCurrentVersion` | `returnsCurrentStateWhenTheClientVersionIsAheadAfterAServerReset` |
| Unchanged state | `sinceVersion` equal to the server version returns `204`, no body, and `no-store` | `suppressesTheStateBodyWhenTheClientAlreadyHasTheCurrentVersion` | `returnsNoContentOnlyWhenTheClientHasTheCurrentStateVersion` |
| Server reset | A client version ahead of the restarted server returns authoritative `200` state, never `204` | `returnsNativeRuntimeChangesAfterTheClientCurrentVersion` | `returnsCurrentStateWhenTheClientVersionIsAheadAfterAServerReset` and `gateway.test.ts` lower-version refresh regression |
| Invalid request | `400`, `application/json; charset=utf-8`, shared `{error:{code,message,retryable}}`, `no-store` | `rejectsMalformedAndNegativeStateVersionsWithTheApiErrorEnvelope`; `apiErrorsUseTheSharedSafeEnvelopeForEveryServerErrorStatus` | `rejectsInvalidStateVersionsWithTheSharedJsonErrorEnvelope`; `usesTheSharedJsonErrorEnvelopeForDesktopApiFailures` |
| Unknown API resource | Any method returns `404` with shared `NOT_FOUND` JSON and `no-store` | `apiErrorsUseTheSharedSafeEnvelopeForEveryServerErrorStatus` | `usesTheSharedJsonErrorEnvelopeForDesktopApiFailures` |
| Known API, wrong method | `405` with shared `METHOD_NOT_ALLOWED` JSON and `no-store` | `apiErrorsUseTheSharedSafeEnvelopeForEveryServerErrorStatus` | `usesTheSharedJsonErrorEnvelopeForDesktopApiFailures` |
| Unexpected API failure | `500`, sanitized shared `INTERNAL_ERROR`, `retryable=true`, `no-store` | `apiErrorsUseTheSharedSafeEnvelopeForEveryServerErrorStatus` | `sanitizesUnexpectedDesktopApiFailures` |
| Temporarily unavailable work | `503`, sanitized shared `SERVER_BUSY`, `retryable=true`, `no-store` | `rejectsTheNinthSimultaneousConnectionWithoutCreatingAnotherWorker` | `reportsTemporarilyUnavailableDesktopApiWorkAsRetryable` |
| Catalog media success | `200`, correct media content type, `Cache-Control: no-cache`; no immutable policy | `catalogMediaRequiresRevalidationInsteadOfImmutableCaching` | `requiresCatalogMediaToRevalidateInsteadOfCachingImmutableUrls` |
| Catalog media rejection | Invalid identifier, wrong suffix, missing catalog key, or unsafe key returns `404`; no fallback HTML | `apiErrorsUseTheSharedSafeEnvelopeForEveryServerErrorStatus`; map/trainer asset regressions | `usesTheSharedJsonErrorEnvelopeForDesktopApiFailures`; `servesCatalogOwnedLocalMapPngAssets` |
| Existing static asset | `200`, extension-derived content type, `Cache-Control: no-cache` | `fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation` | `fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation` |
| SPA navigation | Only extensionless `GET` requests accepting `text/html` may receive `index.html` with `200`, HTML content type, and `no-cache` | `fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation` | `fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation` |
| Missing static asset | Missing script, stylesheet, image, SVG, or source map returns `404` and never index HTML | `fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation` | `fallsBackToTheSpaOnlyForExtensionlessHtmlNavigation` |
| Browser response parsing | `204` is bodyless; non-JSON and malformed JSON fail safely; shared error messages are bounded | Shared browser client | `gateway.test.ts` defensive parsing regressions |
| Connection recovery | One cancellable poll, five-second request watchdog, bounded backoff, visible reconnect/failure state, and authoritative bootstrap after reconnect/reset | Partial-request timeout and close-interruption regressions establish Android recovery | Desktop SSE conflation/deadline tests plus `gateway.test.ts` reconnect/reset regressions |

## Maintenance rule

A browser-visible server contract change is incomplete until both owning server tests are updated or the matrix records why the behavior is transport-specific. Shared browser recovery is tested once in `companion-web/src/gateway.test.ts`; transport liveness remains owned by each server suite.
