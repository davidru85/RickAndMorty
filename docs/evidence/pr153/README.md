# PR #153 — B6 Phase 6.1 review evidence

- **Status:** Active; observed review evidence for TASK-045 and TASK-046.
- **Last verified:** 2026-10-04
- **Owner:** QA & Validation Engineer
- **Authoritative for:** the observations and retained artifacts of this PR review; test policy remains in TESTING.md.
- **Inputs:** [TESTING.md](../../TESTING.md), [UI_SPEC.md](../../UI_SPEC.md), [DEFINITION.md](../../DEFINITION.md), [DECISION_BOARD.md](../../DECISION_BOARD.md) (DEC-119).

The owner authorised emulator execution on 2026-10-04: “Do it yourself. You can use an Android emulator if needed.” DEC-119 records this task-scoped method change. This is Android accessibility evidence, not physical-device performance evidence or an M1 release declaration.

Environment: Pixel_9_Pro AVD, Android 17/API 37, arm64, 1280 × 2856 px, density 480 (3 px/dp), TalkBack 17.0.0.889642762. System font scale was verified as 2.0 for the maximum-text records, and animator duration scale as 0 for reduced motion. The debug APK was assembled from the corrected PR source and installed with `adb install --no-incremental -r`; the real MainActivity remained open. Automated native-graphics cases use SDK 36, English, and in-memory repositories/image seams.

| Checklist item | Observed result and retained evidence |
| --- | --- |
| Contrast (`TEST-A11Y-002`) | [contrast.csv](contrast.csv) records foreground/background pixels from rendered emulator text regions. Body text uses the 4.5:1 threshold, large text 3:1. The token suite also checks composited opacity, the brightest backing behind the translucent status badge, all three stat-label backgrounds, and name/species pairs over 48 sampled dynamic containers. The stale-banner pair is measured by the native Android snapshot and the same automated record; it was not induced by waiting 24 hours on the emulator. Android has no iOS glass surface. |
| Targets (`TEST-A11Y-003`) | The focus records below include measured rectangles. Dividing by 3 gives: Back/Share 48 × 48 dp, filters at least 48 dp high, navigation 209.3 × 80 dp, Favorite 168.7 × 56 dp, Browse characters 246 × 48 dp, Sounds 52 × 48 dp, protocol buttons 180.3 × 53.3 dp, Delete favorites 362.7 × 53.3 dp, Cancel 106 × 53.3 dp and Delete 100 × 53.3 dp. Search exceeds 48 dp in both dimensions. The no-results action was initially clipped to 44.3 dp; the fixed scrollable state exposes its complete target. The regression also measures `SemanticsNode.touchBoundsInRoot`, including Compose's minimum target expansion. |
| TalkBack (`TEST-A11Y-004`) | Actual TalkBack focus changes were driven by emulator hardware touch events (`adb emu event mouse`), with its service kept active by `UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`. Discovery order: search → title/count → four filters → named card; detail: labelled portrait → Back/Share → textual status/name/species → stats/info → Favorite → navigation. Episodes/Favorites: heading → body → Browse characters → navigation. Settings: headings → Sounds → source controls → explanation/delete → navigation. Dialog: heading → explanation → Cancel → Delete. A favourite was activated and then appeared in Favorites. The logs record accessibility focus and labels, not an assertion about recorded speech audio. |
| Maximum text (`TEST-A11Y-005`) | The screenshots below show one-column lists, growing stacked detail stats and complete info text, two-row navigation, empty/error copy and a fitting confirmation dialog. Long empty copy can scroll to its action. Native rendered-text regressions confirm actual scale 2.0 and absence of ellipsized detail values. |
| Reduced motion (`TEST-A11Y-006`) | [Dim](splash-pulse-dim.png) and [bright](splash-pulse-bright.png) emulator frames keep the portal stationary; pixel (640,1250) changes from RGB (157,210,72) to (188,244,99), while pixel (640,1200) stays (73,116,1). The real shell now selects PortraitMotion's 300 ms crossfade policy; a rendered regression proves the previous screen has left by 350 ms. Android's global removal setting also suppresses system-scaled transitions. The separate scale-zero rendered test proves the opacity-only splash signal keeps pulsing. This does not claim a normal-motion shared-element transform was verified. |

Disabled-control text is recorded separately and excluded from the threshold assertion according to [WCAG 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html#inactive-user-interface-components) (primary source checked 2026-10-04). Foreground samples use solid glyph interiors rather than anti-aliasing fringe pixels.

The complete local gate passed; [the command and observed output](verification.txt) are retained. The checked debug APK SHA-256 is `ac3df93aaecdcfcdb5c10b85aa0c7f227b00ea580652c2dbd52f9abcb312ab19`. Clear filters measured 490 × 144 px (163.3 × 48 dp) after TalkBack scrolled the corrected state into view.

## TalkBack records

- [final-max-discovery](final-max-discovery-focus.json)
- [final-max-detail](final-max-detail-focus.json)
- [final-max-detail-actions](final-max-detail-actions-focus.json)
- [final-max-episodes](final-max-episodes-focus.json)
- [final-max-favorites](final-max-favorites-focus.json)
- [final-max-settings](final-max-settings-focus.json)
- [final-max-delete-dialog](final-max-delete-dialog-focus.json)
- [final-max-no-results](final-max-no-results-focus.json)

## Emulator screenshots

- [final-max-list](final-max-list.png)
- [final-max-detail](final-max-detail.png)
- [final-max-detail-top](final-max-detail-top.png)
- [final-max-episodes](final-max-episodes.png)
- [final-max-favorites](final-max-favorites.png)
- [final-max-favorites-loaded](final-max-favorites-loaded.png)
- [final-max-settings](final-max-settings.png)
- [final-max-delete-dialog](final-max-delete-dialog.png)
- [final-max-error](final-max-error.png)
- [final-max-no-results](final-max-no-results.png)
- [final-detail-favorited](final-detail-favorited.png)

Before remediation: [system-bar overlap](insets-before.png), [split navigation labels](navigation-maximum-text-before.png).

## Changed snapshot comparisons

The catalogue's original Box overlaid its subjects; the corrected Column places each subject in its own row on Surface with a complete viewport (UI_SPEC.md §1.2). Empty actions now retain a full 48 dp layout and can be scrolled into view (§6.4/§8/§9). New paired baselines add Episodes, maximum-text detail and maximum-text shell coverage. Unchanged baselines were not accepted as new visual changes.

| Baseline | Original PR | Corrected PR |
| --- | --- | --- |
| `core/designsystem/src/test/snapshots/components-dark.png` | [Before](components-dark-before.png) | [After](../../../core/designsystem/src/test/snapshots/components-dark.png) |
| `core/designsystem/src/test/snapshots/components-light.png` | [Before](components-light-before.png) | [After](../../../core/designsystem/src/test/snapshots/components-light.png) |
| `core/designsystem/src/test/snapshots/states-dark.png` | [Before](states-dark-before.png) | [After](../../../core/designsystem/src/test/snapshots/states-dark.png) |
| `core/designsystem/src/test/snapshots/states-light.png` | [Before](states-light-before.png) | [After](../../../core/designsystem/src/test/snapshots/states-light.png) |
