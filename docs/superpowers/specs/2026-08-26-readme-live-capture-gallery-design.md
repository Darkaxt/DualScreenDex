# README Live-Capture Gallery Design

**Date:** 2026-08-26

## Objective

Replace the README's old debug/reference viewport gallery with a concise gallery captured from the real signed `v1.1.0-rc.66` APK running on the AYN Thor. The gallery must show the current product rather than synthetic data, simulated routes, or implementation tooling.

## Evidence source

- Recording: `screen-20260826-111430.mp4`
- SHA-256: `B485F6F0CD2BCA3BB773A4567611F1593C30932F344EE523458BF81E62C09053`
- Video: 1240 × 1080 H.264, 196.903 seconds
- Installed binary verified through ADB: `versionName=1.1.0-rc.66`, `versionCode=1010066`

The recording's presentation timestamps are discontinuous. Frame order, not media timestamps, is the authoritative sequence for analysis and extraction.

## Selection pipeline

1. Decode every video frame sequentially.
2. Compare reduced-resolution consecutive frames and retain stable runs of at least 0.25 seconds.
3. Collapse perceptually equivalent runs across the recording.
4. Group remaining states by visible feature and interaction event.
5. Select one settled frame for each distinct feature represented in the gallery.
6. Extract selected frames from the original-resolution frame stream and encode them as lossless WebP assets.

The analysis must not select arbitrary interval samples. Transitions, touch feedback, repeated states, system overlays, and recorder artifacts are rejected.

## Gallery coverage

The final seven-frame gallery covers:

1. Live local map with route identity, in-game clock, player sprite, tracking controls, and discovered POIs.
2. Wild-encounter rarity assessment.
3. Selected-attack details during a wild encounter.
4. Trainer Card with live identity and progress fields.
5. Party Pokémon detail with rarity, nature, ability, experience, and current stats.
6. Pokédex height comparison using the live trainer sprite.
7. Parsed ability behavior with concrete activation and power conditions.

## Privacy and presentation rules

- Exclude the Android performance panel and every debug or setup screen.
- Exclude loading states from the primary gallery.
- Include the real Trainer Card without redaction, as explicitly approved by the recording owner.
- Exclude map frames that reveal the player-named home label.
- Do not edit, blur, stage, or synthesize gameplay information inside selected frames.
- Use a consistent 1240 × 1080 aspect ratio and lossless WebP encoding.
- Use descriptive alt text and brief captions that state what each image demonstrates.

## README changes

- Replace both old screenshot rows under `Thor-first UI direction`.
- Replace the text describing a 406 × 354 debug APK and streamed ZIP with an accurate signed-RC66 live-capture statement.
- Update the stale candidate reference from `v1.1.0-rc.63` to `v1.1.0-rc.66` without changing compatibility claims elsewhere.
- Keep the existing product description and detailed feature documentation intact.

## Acceptance criteria

- Exactly seven new live-capture assets are referenced by the README and exist in `docs/images/live/`.
- All seven images are 1240 × 1080 lossless WebP files from the verified recording.
- Only the explicitly approved Trainer Card exposes player identity and Trainer ID; no selected image exposes system controls or debug UI.
- The gallery represents seven different product capabilities without duplicate states.
- Old debug/reference gallery paths are no longer referenced from the README.
- Markdown image tags have useful alt text and render in readable `3 + 2 + 2` rows.
- `git diff --check` passes and a scripted gallery audit confirms asset count, dimensions, format, and references.
