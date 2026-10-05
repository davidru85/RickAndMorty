# iOS design system (`TASK-052`)

The iOS half of the design system (`UI_SPEC.md` §3, §4.2, §6.4, §9). It mirrors
`:core:designsystem` on Android and reads the same committed token export
(`docs/figma/tokens.json`, `DEC-022`/`DEC-102`).

## The two visual paths

Every glass component is **one** view with two render paths, and the selection lives in **one** place —
`GlassSurface.swift` (`GUIDELINES.md` §6.2). No component calls `glassEffect` itself, so no call site
duplicates the check and the two paths cannot drift apart.

| Path | Selected when | Rendering |
| --- | --- | --- |
| `GlassMaterial.glass` | `#available(iOS 26, *)` and Reduce Transparency is off | `.glassEffect` / `.glass` / `.glassProminent` |
| `GlassMaterial.material` | iOS 18 (the deployment floor), Reduce Transparency off | `.regularMaterial` + `Glass/Fill` + the rim |
| `GlassMaterial.opaqueMaterial` | **Reduce Transparency on, every OS** (`UI_SPEC.md` §9) | `.thickMaterial` over `Surface Container`, opaque |

`GlassMaterial.resolved(reduceTransparency:)` is the availability check; the environment key
`\.multiverseGlassPath` is the preview/test seam that lets `TEST-UI-010` snapshot each path on one OS
(the fallback is otherwise unreachable on iOS 26). `TEST-UI-015` covers Reduce Transparency.

## Components and the `UI_SPEC.md` section each implements

Every component carries this mapping in its own doc comment; this table is the index.

| Type | `UI_SPEC.md` | Figma component (§1.2) |
| --- | --- | --- |
| `GlassCharacterCard` | §4.2 | `iOS/Glass character card` (`22:264`) |
| `GlassInfoRow` | §4.2 | `iOS/Glass info row` (`22:265`) |
| `GlassStatusCapsule` | §4.2 | — (status capsule) |
| `GlassPanel` | §4.2 ("Frosted panel") | — |
| `GlassSegmentedControl` | §4.2 | `iOS/Glass segmented control` (`22:271`) |
| `GlassSearchField` | §4.2 | `iOS/Glass search field` (`22:281`) |
| `GlassIconButton` | §4.2 | `iOS/Glass icon button` (`25:287`) |
| `GlassTextButton` | §4.2, §6.4 | `iOS/Glass text button` (`102:197`) |
| `GlassTabBar` / `GlassTabBarItem` | §4.2, §6.4 | `iOS/Glass tab bar` (`102:255`), `iOS/Glass tab item` (`117:1369`) |
| `EmptyState` | §6.4 | `iOS/Empty state` (`102:256`) |
| `MultiverseColors` | §3.1 | `Multiverse · M3 Scheme` |
| `MultiverseBrandColors`, `MultiverseGlassColors`, `MultiverseLabelColors` | §3.2 | `Multiverse · Brand` |
| `MultiverseDimensions` | §3.3 | `Multiverse · Dimensions` |
| `MultiverseType` | §3.4 | — (SF Pro, Dynamic Type) |

`GlassShape`, `glassSurface(_:tint:shadow:)`, `glassButton(_:tint:shape:)` and `GlassContainer` are
the shared mechanism of §3.5/§4.2, not standalone components.

## Tokens and parity

`Tokens.swift` names every value the export carries, and `MultiverseTokens` maps each to its export
variable name. `MultiverseTokensParityTests` (`TEST-UNIT-035`) compares **both** directions against
`docs/figma/tokens.json`:

- a Swift value that drifts from the export fails;
- an exported variable that no token maps fails — the iOS half maps the whole export, including the
  `Glass/*` and `Label/*` families the Kotlin half lists as exclusions, so its exclusion list is
  empty and that is asserted.

## Tests

- `iosApp/Tests/MultiverseTokensParityTests.swift` — `TEST-UNIT-035`.
- `iosApp/Tests/GlassComponentRenderingTests.swift` — `TEST-UI-010` (both paths render) and
  `TEST-UI-015` (Reduce Transparency renders) as render smoke checks; the committed snapshot
  baselines are `TASK-059`'s (`DEC-025`).

Run the gate and the tests with:

```sh
bash tools/swift-lint.sh iosApp
xcodebuild test -scheme MultiverseExplorer \
  -destination 'platform=iOS Simulator,id=BCE6F3E9-9A9E-4B78-BBDD-6859FFB7C24B'
```
