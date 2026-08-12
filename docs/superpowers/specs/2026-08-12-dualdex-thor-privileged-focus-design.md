# DualDex AYN Thor Privileged Focus Design

Date: 2026-08-12

## Objective

Prevent AYN Thor focus automation from crashing when the firmware classifies
`screen_focus_lock` as a secure setting. DualDex must acquire only the privilege
needed for this one device-global setting, prefer Shizuku, fall back to root
after an explicit enable action, and fail closed with a disabled control and a
truthful warning.

## Confirmed Failure

The released app calls `Settings.System.putInt` after Android grants ordinary
Modify System Settings access. On the tested AYN Thor firmware that call throws
`IllegalArgumentException: You cannot keep your settings in the secure
settings.` The exception escapes the activity-result callback and
`onPostResume`, terminating the process.

The vendor assistant is a privileged system app with `WRITE_SECURE_SETTINGS`.
DualDex is not a system app, and ordinary `WRITE_SETTINGS` cannot provide the
same authority. The existing permission screen is therefore not a valid path
for this setting and will no longer be used by Thor focus automation.

## Chosen Approach

Use an asynchronous, ordered privileged-provider chain:

1. Shizuku or Sui, using the official Shizuku API and permission flow.
2. Root through `su`, only after the same explicit user enable action and only
   when Shizuku is unavailable, denied, or fails the exact setting operation.
3. Unavailable state when neither provider completes a verified read/write.

Shizuku runs a small UserService with shell or root identity. The service
executes only fixed `settings get secure screen_focus_lock` and
`settings put secure screen_focus_lock <0|1|2>` operations. This avoids the
deprecated Shizuku process API and keeps privileged work behind a narrow typed
interface. The root provider executes the same fixed operations with `su`.
Neither provider accepts arbitrary command text.

All process and binder work runs away from the Android main thread. No timeout
is used to cancel a permission prompt or privileged operation. One operation is
in flight at a time, and completion is delivered back to the activity through
an explicit result.

## Rejected Approaches

### Continue using Modify System Settings

Rejected because the live firmware routes this key to secure settings and the
ordinary API throws even after permission is granted.

### Root-only implementation

Rejected because it unnecessarily excludes non-rooted devices already running
Shizuku through wireless debugging and contradicts the requested privilege
order.

### Deprecated Shizuku `newProcess`

Rejected because the official API is preparing to remove it. A typed
UserService is more durable and keeps command execution isolated.

## Privilege and Interaction Flow

### Passive startup and resume

DualDex may inspect Shizuku binder availability and already-granted Shizuku
permission. It must not request Shizuku permission or invoke `su` during app
startup, resume, background polling, or ordinary settings rendering.

If an already-authorized Shizuku or Sui service becomes available, the control
returns to a ready state automatically. Binder death makes the state
unavailable without crashing.

### Explicit enable action

When the user enables `thorTopScreenFocus`:

1. If Shizuku is alive and already authorized, use it immediately.
2. If Shizuku is alive but permission is undecided, request permission once.
3. After grant, retry the pending focus operation once through Shizuku.
4. If Shizuku is absent, denied, permanently denied, disconnected, or cannot
   perform the verified secure-setting operation, invoke `su` automatically.
5. If root succeeds, continue through the root provider.
6. If root is absent, denied, or fails, reset the desired setting to off,
   disable the toggle for the current availability state, and publish the
   warning `Thor focus unavailable — Shizuku or root access is required.`

The provider chain is single-flight. Repeated state publications or activity
resumes cannot create duplicate Shizuku dialogs, root prompts, or writes.

### Disable, release, and restoration

DualDex records the prior secure value only after a provider successfully reads
the current value and before it writes Top mode. It records ownership only after
the write is verified.

When ownership ends, DualDex restores the recorded value through the best
currently authorized provider, still preferring Shizuku. A failed restoration
retains the ownership record and warning so a later authorized resume can retry;
it never falsely claims the setting was restored.

If the global value changes externally while DualDex owns it, the existing
ownership rule remains: do not overwrite the external value and release local
ownership.

## State Model and API

Replace the current string-only permission interpretation with an immutable
control state containing:

- `status`: `ACTIVE`, `READY`, `PERMISSION REQUIRED`, or `UNAVAILABLE`;
- `available`: whether the toggle may currently initiate or maintain focus;
- `provider`: `SHIZUKU`, `ROOT`, or absent;
- `warning`: a user-facing warning or absent;
- `operationInFlight`: used to disable duplicate interaction while permission
  or a privileged write is pending.

The companion API exposes the availability and warning fields alongside the
status. The web UI performs no privilege inference.

The persisted `thorTopScreenFocus` setting remains device-global. If the full
provider chain fails during an explicit enable, production writes the setting
back to `false` so the persisted state agrees with the disabled UI and actual
device state.

## UI Contract

The Settings page keeps the existing `Keep controls on top screen` toggle.

- While a privileged operation is running, the toggle is disabled and the
  status reports the current operation.
- When focus is active, the status identifies the selected provider.
- When neither path is usable, the toggle is off and disabled and the warning
  appears directly beside or immediately below the control.
- When Shizuku becomes ready later, the control re-enables automatically.
- The UI never directs the user back to Android's Modify System Settings page.

## Error Handling

- Every Shizuku API call is guarded by binder-alive and permission checks and
  catches the runtime failures documented by the API.
- Every privileged command validates its exit code and parses only the values
  `0`, `1`, and `2`.
- Unexpected output, binder death, process failure, permission denial, or an
  invalid current value produces a typed failure and never escapes to the
  activity lifecycle.
- Shizuku failure advances once to root; root failure ends the chain. There is
  no denial loop.
- Ownership persistence failures and restore failures remain explicit failures;
  the app does not erase recovery state prematurely.

## Component Boundaries

### `ThorFocusPrivilegedProvider`

Provides typed asynchronous read and write operations plus provider identity.
Implementations are Shizuku UserService and root `su`.

### `ThorFocusAccessCoordinator`

Owns provider ordering, Shizuku permission continuation, single-flight state,
root fallback, and availability publication. It never decides display policy.

### `ThorFocusController`

Retains ownership, prior-value, Top-mode enforcement, external-change, and
restoration rules. It consumes typed read/write outcomes rather than Android
settings APIs directly.

### Activity and runtime bridge

The activity registers and removes Shizuku binder and permission listeners,
delivers explicit user enable actions to the coordinator, and publishes state
to the existing runtime. Lifecycle callbacks may refresh passive availability
but cannot launch permission or root prompts.

### Web Settings

Renders the state supplied by Android, disables the control when required, and
shows the warning. It cannot invoke a privileged provider directly.

## Test Strategy

Implementation follows RED/GREEN test-driven development.

### Android unit tests

- The original `Settings.System.putInt` exception becomes a typed failure and
  never escapes the lifecycle boundary.
- Already-authorized Shizuku is selected before root.
- A single Shizuku permission request resumes the pending operation once.
- Shizuku absence, denial, binder death, and verified write failure each advance
  to root exactly once.
- Root is never invoked during startup or passive resume.
- Root success acquires ownership and records the provider.
- Both providers failing resets the desired preference, disables the control,
  and publishes the exact warning.
- Concurrent/repeated sync requests remain single-flight.
- Restoration uses the best authorized provider, preserves recovery state on
  failure, and honors external setting changes.
- Command construction accepts only the fixed key, secure namespace, and modes
  0 through 2.

### API and web tests

- Availability, provider, operation, and warning fields round-trip through the
  companion API.
- The toggle is disabled during pending and unavailable states.
- The warning is adjacent to the disabled control.
- Ready/active Shizuku state and active root state render truthfully.
- No Modify System Settings instructions remain in this feature.

### Verification gates

- Focused Android, companion-core, and web tests.
- Full companion-core, companion-server, companion-web, and app unit suites.
- Android lint and release APK assembly.
- Signed artifact and signer verification.
- Public GitHub prerelease publication with `draft=false`.
- Download the public APK, verify its hash and signer, and perform exactly one
  ADB install/update on the authorized AYN Thor.

## Device Boundary

Release deployment is limited to installing the exact publicly downloaded APK.
Do not launch the app, grant Shizuku or root permission, change settings, send
input, capture screens, or perform automated validation afterward. The user
will exercise and validate the permission flow manually.

## Success Criteria

- Granting ordinary Modify System Settings can no longer crash DualDex because
  Thor focus no longer uses that permission path.
- One explicit enable action automatically attempts Shizuku first and root
  second.
- Startup and resume never create an unsolicited root prompt.
- Focus acquisition and restoration are verified before the UI claims success.
- If neither provider works, the preference is off, the toggle is disabled, and
  the warning is visible beside it.
- The next public prerelease is not a draft and the exact published APK is
  installed once without other device interaction.
