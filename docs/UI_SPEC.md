# UI_SPEC.md - UI/UX Visual Specification

## 1. Component Hierarchy
- `MainScreen`: Navigation host.
- `CharacterListScreen`: LazyColumn of CharacterCards.
- `CharacterDetailScreen`: Detailed profile with high-res image.

## 2. Theme Definitions
- **Colors:** Rick and Morty inspired palette.
- **Typography:** Bold, modern sans-serif.

## 3. Navigation Flow
- `List` $\rightarrow$ `Details` (via ID).

## 4. Image Caching Logic
- Library: Coil/Glide.
- Strategy: Memory cache $\rightarrow$ Disk cache $\rightarrow$ Network.
