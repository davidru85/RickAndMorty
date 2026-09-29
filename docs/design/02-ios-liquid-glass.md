- **Status:** Superseded as a specification — historical input, kept for provenance
- **Last verified:** 2026-09-29
- **Owner:** UI/UX Designer
- **Authoritative for:** nothing. This is the prompt used to generate the iOS Figma page. The normative visual specification is `../UI_SPEC.md`.
- **Inputs:** `../../assessment.md`

> **Not normative.** Where this brief disagrees with `../UI_SPEC.md`, `UI_SPEC.md` wins. Known divergences:
> - The brief's "Dynamic filtering using elegant, pill-shaped segmented controls" is narrowed by `UI_SPEC.md` §6.2 to exactly four status options, with no species or gender filters.
> - The brief assumes an iOS-native design without naming a platform floor; the project sets iOS 18.0 minimum with Liquid Glass behind an availability check (`DEC-008`).
> - Figma library kit references in the brief differ in version from the APIs used in code; `UI_SPEC.md` §4.2 is the binding component specification.

### ROLE
Act as a Senior Product Designer specializing in iOS development and Apple's Human Interface Guidelines (HIG). Your goal is to generate high-fidelity, professional UI/UX prototypes for an iOS application.

### PROJECT CONTEXT
Project Name: "Multiverse Explorer" (Rick and Morty API Client).
Context: This is the iOS counterpart to a KMP (Kotlin Multiplatform) project. The design must follow an "iOS-native" philosophy, utilizing SwiftUI-centric aesthetics. While the Android version uses Material 3, this iOS version must utilize a "Liquid Glass" aesthetic—combining high-end glassmorphism with fluid, organic elements.

### DESIGN LANGUAGE: LIQUID GLASS (iOS)
- Visual Identity: A sophisticated, premium "Glassmorphism" approach. Use heavy backdrop blurs (frosted glass effect), subtle specular highlights, and soft organic shapes (Squircles).
- Color Palette: Sophisticated use of transparency. Base colors derived from the "Rick and Morty" universe (Portal Green, Cosmic Violet) but applied as subtle tints/glows behind translucent layers.
- Typography: Primary use of San Francisco (SF Pro) with dynamic type scaling. High-end editorial feel.
- Layout: iOS-native spacing, utilizing SF Symbols for iconography. No heavy borders; depth is created through blur, light, and layering (Z-axis).
- Components: Use of glass cards with thin, subtle light-catching borders. Smooth, fluid transitions and high-end micro-interactions.

### USER FLOW & SCREEN REQUIREMENTS
Generate a cohesive set of high-fidelity screens:

1. Splash Screen:
   - Minimalist and premium. 
   - Centralized logo with a liquid/glass refraction effect. 
   - Smooth, ethereal background animation feel.

2. Character Discovery (Home/List Screen):
   - A sophisticated grid or list of characters using "Glass Cards".
   - Translucent search bar integrated into the navigation area.
   - Smooth scrolling with parallax effects on character images.
   - Dynamic filtering using elegant, pill-shaped segmented controls.

3. Character Detail Screen (Immersive Glass View):
   - An immersive, full-screen layout where the character's image acts as a blurred background layer (Liquid Glass effect).
   - Information is presented on top of "frosted" translucent panels.
   - Smooth transition: The UI should feel like it's floating over the character's portrait.
   - Highly legible typography with subtle drop shadows for contrast against varying backgrounds.
   - An elegant, minimalist Floating Action Button or integrated icon for "Favorite" action.

### TECHNICAL SPECIFICATIONS FOR OUTPUT
- Aspect Ratio: 9:19.5 (iPhone 14/15 Pro style).
- Style: Premium, clean, depth-heavy, sophisticated.
- Consistency: Ensure the "Liquid Glass" elements look like they are made of physical glass, with realistic refraction and light dispersion.
- Goal: To demonstrate mastery over iOS-specific design patterns (Glassmorphism, SF Symbols, and depth).
