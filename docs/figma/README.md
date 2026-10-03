# docs/figma/ — Rendered design exports

- **Status:** Active (exports committed 2026-10-03; see the export log)
- **Last verified:** 2026-10-03
- **Owner:** UI/UX Designer
- **Authoritative for:** nothing. This directory holds rendered PNG exports of the Figma source so that reviewers can see the design without a Figma account.
- **Inputs:** the Figma file [Rick & Morty](https://www.figma.com/design/nFQdxd23Kk4rNI7G4iHDUr/Rick---Morty)

## Why this directory exists

The Figma source file requires project membership and returns **403** to anonymous clients (verified 2026-09-29). `UI_SPEC.md` §1, §1.1 and §1.2 reference it by node id, which makes the visual specification unverifiable for anyone without access — and a reviewer cloning this repository is exactly that person (`RISK-008`, `CONF-05` → `DEC-045`).

Committed exports fix that: the reviewer sees the design in the repository, the screenshot tests compare against a stable committed reference, and the Figma links in `UI_SPEC.md` become a convenience rather than a dependency.

## Expected export set

Every frame of `UI_SPEC.md` §1.1 (screens) **and** §1.2 (local components) has a committed PNG, named after its frame (`TASK-035`, `UI_SPEC.md` §1.2):

| File | Source frame | Node |
| --- | --- | --- |
| `01-splash-android.png` | 01 · Splash (Android) | `20:1620` |
| `01-splash-ios.png` | 01 · Splash (iOS) | `29:291` |
| `02-discovery-android.png` | 02 · Discovery (Android) | `20:1735` |
| `02-discovery-ios.png` | 02 · Discovery (iOS) | `29:381` |
| `03-detail-android.png` | 03 · Detail (Android) | `21:1217` |
| `03-detail-ios.png` | 03 · Detail (iOS) | `26:452` |
| `04-episodes-android.png` · `04-episodes-ios.png` | 04 · Episodes placeholder | `101:499` · `102:269` |
| `05-settings-android.png` · `05-settings-ios.png` | 05 · Settings | `101:568` · `102:322` |
| `05b-settings-delete-android.png` · `05b-settings-delete-ios.png` | 05b · Settings · Delete favorites confirmation | `122:1293` · `123:529` |
| `06-favorites-android.png` · `06-favorites-ios.png` | 06 · Favorites empty state | `101:637` · `102:375` |
| `10-launcher-android.png` | Android launcher icon board | `59:240` |
| `10-appicon-ios.png` | iOS app icon board | `59:930` |
| `11-portal-logo.png` | `Brand/Portal logo` (§1.2) | `16:13` |
| `12-status-badge-android.png` | `Android/Status badge` (§1.2) | `16:23` |
| `13-character-card-android.png` | `Android/Character card` (§1.2) | `16:40` |
| `14-info-list-item-android.png` | `Android/Info list item` (§1.2) | `16:41` |
| `15-stat-tile-android.png` | `Android/Stat tile` (§1.2) | `16:48` |
| `16-nav-bar-android.png` | `Android/Navigation bar` (§1.2) | `117:887` |
| `17-empty-state-android.png` | `Android/Empty state` (§1.2) | `101:483` |
| `18-glass-card-ios.png` | `iOS/Glass character card` (§1.2) | `22:264` |
| `19-glass-info-row-ios.png` | `iOS/Glass info row` (§1.2) | `22:265` |
| `20-glass-segmented-ios.png` | `iOS/Glass segmented control` (§1.2) | `22:271` |
| `21-glass-search-ios.png` | `iOS/Glass search field` (§1.2) | `22:281` |
| `22-glass-icon-button-ios.png` | `iOS/Glass icon button` (§1.2) | `25:287` |
| `23-glass-tab-bar-ios.png` | `iOS/Glass tab bar` (§1.2) | `102:255` |
| `24-glass-tab-item-ios.png` | `iOS/Glass tab item` (§1.2) | `117:1369` |
| `25-glass-text-button-ios.png` | `iOS/Glass text button` (§1.2) | `102:197` |
| `26-empty-state-ios.png` | `iOS/Empty state` (§1.2) | `102:256` |

Naming follows the screen numbering in `UI_SPEC.md` §1.1/§1.2 and §10. Export at 2× the frame size, with the frame background included, so the files are directly comparable with the Android and iOS screenshot baselines described in `TESTING.md`. `tokens.json` (the variable export, `DEC-102`) lives in this directory too and has its own schema in `DESIGN.md` §4.3.

## Export procedure

1. Open the Figma file with project access (the file is not public).
2. Select each frame listed above and export it as PNG at 2×.
3. Name the files exactly as in the table and place them in this directory.
4. Add the exports in the same change that updates `README.md` §3, which currently states that screenshots are pending.
5. Record the export date in the *Export log* below. Exports are a snapshot: when Figma changes, re-export and regenerate the affected screenshot baselines in the same pull request.

## Export log

| Date | Exported by | Files | Figma version note |
| --- | --- | --- | --- |
| 2026-10-03 | Implementation Engineer (Android), through the authenticated Figma connector | 32 PNGs: the 12 screen frames of §1.1 (both platforms) and the 20 component frames of §1.2, plus `tokens.json` (`DEC-102`) | Rendered by the Figma Dev Mode asset API from the actual frames (`defaultFormat=png`, `defaultScale=2`); every file's PNG IHDR was verified against 2× the frame box, and four component frames (20–21, 25–26) render their visual overflow beyond the layout box, so their verified size is 2× the scale-1 export, not 2× the layout box |

## Current state

The export set is committed: 32 PNGs covering every §1.1 screen frame and every §1.2 component frame, plus `tokens.json` (`DEC-102`). They are the design evidence a reviewer without Figma access can open, and the directory's export log records when and how each was produced.

Still open, and not satisfied by this directory:

- in-app screenshots of the running apps join `README.md` once the first runnable milestone is verified (`GAP-008`, `TASK-044`);
- the Android and iOS **screenshot baselines** of `TESTING.md` are recorded from the running apps (Roborazzi/swift-snapshot-testing, `TASK-045`/`TASK-059`) and do not depend on this directory.

Do not substitute downloaded mock portraits or generated imagery for the exports: the screens must be rendered from the actual Figma frames, or the comparison claim becomes false.
