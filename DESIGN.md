# DESIGN.md - System Architecture Design

## 1. Architectural Pattern
- **Pattern:** Clean Architecture + MVVM.
- **Layers:** Data $\rightarrow$ Domain $\rightarrow$ Presentation.

## 2. Module Boundaries
- `:data`: API services, Repositories implementations, Local sources.
- `:domain`: Use Cases, Repository interfaces, Domain models.
- `:presentation`: ViewModels, Compose Screens, State management.

## 3. Dependency Injection
- Framework: Hilt/Koin.
- Strategy: Singleton for repositories, Factory for ViewModels.

## 4. Class Diagram Specification
- [Placeholder for Mermaid/PlantUML diagram]
