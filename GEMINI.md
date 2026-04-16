# ChatFolio — AI Developer Guidelines (GEMINI.md)

## 1. Project Overview
ChatFolio is a local-first, chat-driven Android portfolio tracker. There is no backend — all data lives on-device in Room (SQLite). The app communicates directly with two external APIs: the Gemini LLM (via BYOK configuration) for natural language reasoning, and Yahoo Finance (unofficial REST) for EOD prices. 

The entire UX is designed around a single chat screen. The AI responds with plain text or rich inline composable cards.

---

## 2. Architecture & Design Principles

### Simplicity & Readability (KISS & YAGNI)
- **You Aren't Gonna Need It (YAGNI):** Do not add unnecessary functions, interfaces, or complex batch handlers unless strictly required to solve a proven, severe problem. Keep things simple and clear.
- **Readability over premature optimization:** Standard, linear flows are preferred over complex abstractions if the actual scale (e.g., chat-driven inputs) doesn't justify it.
- **Keep state local if possible:** Use the simplest mechanism to solve the problem. For example, use a local `remember { mutableStateOf() }` for transient UI states rather than complex ViewModel orchestration or domain modeling.

### Core Architectural Rules
- **Layering:** UI → ViewModel → Domain → Data. Never skip layers or point backwards.
  - `ChatViewModel` orchestrates Compose UI state but does not own business logic.
  - `ChatInteraction` (domain) owns the LLM prompting, tool parsing, and orchestration.
  - `PortfolioRepository` (data) is the exclusive source of truth for Room database mutations.
- **SOLID & OOP:**
  - Separate business logic, data fetching, and persistence via Dependency Inversion.
  - Hide internal state (`StateFlow` vs `MutableStateFlow`).
- **Database (Room):** `AppDatabase.kt` defines entities and versions only. Migrations MUST be provided in `DatabaseMigrations.kt` — never use destructive migrations.
- **Price Caching:** Yahoo Finance is the source of truth for trading dates. The Data layer natively handles API checks and local caching.

### Code Review Checklist
When creating Pull Requests, prioritize logic and architecture over minor nitpicks:
1. **Architecture:** Does this belong here? Does it violate layering?
2. **Readability:** Is it over-engineered? Can someone else read this easily?
3. **Functionality:** Does this strictly and simply solve the user intent?
4. **Tests:** Is this new behavior reliably covered by TDD?

---

## 3. Development Workflow & Testing

1. **Test-Driven Development (TDD):** ALWAYS write tests before or alongside new features to prove they work. Untested code is incomplete.
2. **Branching:** Use isolated branches (`{user_name}/{feature_name}`). NEVER push directly to `main`.
3. **Consent to Merge:** Wait for user consent before merging PRs.
4. **Atomic Commits:** Follow Conventional Commits (`feat:`, `fix:`, `refactor:`). Keep them granular.
5. **Progress Tracking:** Check the `checkpoints/` folder. Progress is recorded incrementally in session files.

### Standard Commands
```bash
./gradlew ktlintCheck            # Check Kotlin code style
./gradlew ktlintFormat           # Auto-fix most style violations
./gradlew testDebugUnitTest      # Run all unit tests
./gradlew assembleDebug          # Build debug APK
```
*Note: GitHub Actions enforces these commands on every PR. Run them locally to not block CI!*

---

## 4. Technical Reference

### Package Structure
```
com.chatfolio
├── data/
│   ├── local/          # Room DB, DAOs, Entities
│   ├── network/        # Ktor Clients (Yahoo)
│   └── repository/     # Data aggregation & persistence
├── di/                 # Hilt Modules
├── domain/
│   ├── port/           # Interfaces (e.g., LlmEngine)
│   └── usecase/        # Agnostic Business Logic & Agent Parsers
└── ui/
    ├── cards/          # Stateless Composable UI components
    └── chat/           # UI Stateful Screens & ViewModels
```

### External API Rules
- **Yahoo Finance:** No auth required. Rate limits apply. Returns `meta.regularMarketTime` which determines market holidays/weekends implicitly.
- **Gemini SDK (BYOK):** Standard integration. Keys are injected via EncryptedSharedPreferences and are strictly secured.

### Future Roadmap
- Live UI data refresh loops
- Advanced charts & Donut visualizers
- Short-selling support
- Trades management page
- Multi-currency portfolio valuations
