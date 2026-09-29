# AGENTS.md - Master Instruction Manual

This document defines the specialized AI agents required to architect and implement the Rick and Morty Character Review application. These agents are designed to work in a pipeline, ensuring that each phase of development is grounded in the technical specifications and requirements defined in `assessment.md`.

## 1. Requirements Analyst Agent
**Core Objective:** Extract and formalize all functional and non-functional requirements from `assessment.md` and the Rick and Morty API documentation to create a traceability matrix.
**Required Inputs:** `assessment.md`, https://rickandmortyapi.com/
**Expected Output / Deliverable:** A formalized `REQUIREMENTS.md` file containing a categorized list of Must-Have, Should-Have, and Could-Have features (MoSCoW method), including specific UX and performance constraints.

## 2. API Architect Agent
**Core Objective:** Define the data contract between the Android application and the Rick and Morty API, focusing on efficiency and resilience.
**Required Inputs:** `REQUIREMENTS.md`, API documentation (https://rickandmortyapi.com/)
**Expected Output / Deliverable:** `API_SPECS.md` containing endpoint mappings, DTO (Data Transfer Object) definitions, error code mappings, and a caching strategy specification (Response caching).

## 3. System Architect Agent
**Core Objective:** Design the high-level software architecture applying SOLID principles and modern Android patterns to ensure maintainability and scalability.
**Required Inputs:** `REQUIREMENTS.md`, `API_SPECS.md`
**Expected Output / Deliverable:** `DESIGN.md` detailing the architectural pattern (e.g., Clean Architecture with MVVM), module boundaries, dependency injection strategy, and a class diagram specification.

## 4. UI/UX Designer Agent
**Core Objective:** Translate the "image-oriented" requirement into a detailed visual specification and component hierarchy using Jetpack Compose.
**Required Inputs:** `REQUIREMENTS.md`, `DESIGN.md`
**Expected Output / Deliverable:** `UI_SPEC.md` containing a component breakdown, theme definitions (colors, typography), navigation flow, and image caching specifications (e.g., Coil/Glide integration logic).

## 5. Implementation Engineer Agent
**Core Objective:** Translate the specifications into functional code, focusing on the "cleanliness of code" and technical decisions.
**Required Inputs:** `DESIGN.md`, `API_SPECS.md`, `UI_SPEC.md`
**Expected Output / Deliverable:** Production-ready Android source code implementing the defined architecture, following the project's style guide.

## 6. QA & Validation Agent
**Core Objective:** Ensure the implementation meets all specified requirements and is resilient to edge cases.
**Required Inputs:** `REQUIREMENTS.md`, `API_SPECS.md`, Implementation source code.
**Expected Output / Deliverable:** A test suite specification (Unit, Integration, and UI tests) and a validation report confirming the fulfillment of all MVP and Extra requirements.
