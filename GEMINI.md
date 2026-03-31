# ChatFolio — GEMINI.md

## Project Overview

ChatFolio is a local-first, chat-driven Android portfolio tracker. There is no backend — all data lives on-device in Room/SQLite. The app talks directly to two external APIs: the Gemini LLM (via BYOK) for AI, and Yahoo Finance (unofficial REST) for EOD prices and dividends.

The entire UX is a single chat screen. The AI responds with either plain text or rich inline composable cards (portfolio summary, trade confirmation, holdings table, charts).

## AI Agent Development Workflow

The following rules MUST be strictly adhered to by any LLM or AI agent interacting with this repository:

1. **No Direct `main` Commits**: NEVER commit or push code directly to the `main` branch.
2. **Require Consent to Merge**: NEVER merge any branch into `main` without explicit, unambiguous consent from the user. Even if a PR is created, wait for the user to approve and merge it, or wait for them to explicitly command you to do so.
3. **Branch Naming**: ALWAYS create branches using the `{user}/feature-name` format (e.g., `wchu/add-ci-pipeline`).
4. **Conventional Commits**: You MUST follow conventional commit patterns for commit messages (e.g., prefixing with `feat:`, `chore:`, `fix:`, `refactor:`, `docs:`, etc.).
5. **Feature Isolation**: ALWAYS develop new features, bug fixes, or documentation updates in your isolated branch.
6. **Test-Driven Development (TDD)**: ALWAYS use Test-Driven Development. When adding a new feature, you MUST write corresponding tests (unit, integration, or UI) and verify them. Untested features are considered incomplete.
7. **Continuous Integration**: EVERY pull request is automatically verified by GitHub Actions. You MUST ensure `ktlintCheck` and `testDebugUnitTest` pass before requesting a review.


## Continuous Integration (GitHub Actions)

ChatFolio uses GitHub Actions for automated quality control. The workflow is located in `.github/workflows/ci.yml` and performs:
- **Linting**: Enforces Kotlin style via `ktlint`.
- **Unit Tests**: Runs all JVM unit tests.
- **Build Verification**: Ensures the debug APK assembles correctly.

## Package Structure

```
com.chatfolio
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # Room database definition (version + entity list only)
│   │   ├── dao/                    # One DAO interface per entity
│   │   └── entity/                 # Room @Entity data classes
│   ├── network/
│   │   ├── YahooFinanceClient.kt   # Ktor-based Yahoo Finance HTTP client
│   │   └── model/                  # @Serializable DTOs for Yahoo chart API response
│   └── repository/
│       ├── GeminiEngine.kt         # LlmEngine implementation (Google GenAI SDK)
│       ├── PortfolioRepository.kt  # Trade persistence: add/delete/update transactions + holdings
│       └── SettingsRepository.kt  # API key read/write via EncryptedSharedPreferences
├── di/
│   ├── AiModule.kt                 # Hilt bindings for LlmEngine
│   ├── DatabaseModule.kt           # Hilt bindings for Room DAOs
│   └── NetworkModule.kt            # Hilt bindings for HttpClient and YahooFinanceClient
├── domain/
│   ├── port/
│   │   └── LlmEngine.kt            # Interface: abstracts over LLM providers
│   └── usecase/
│       ├── ChatBotAgent.kt         # Orchestrates LLM tool calls and chat state
│       ├── HoldingWithPrice.kt     # Output model: holding + live price + P&L
│       └── PortfolioManager.kt     # Resolves live prices per holding via Yahoo + Room cache
└── ui/
    ├── cards/                      # Composable adaptive cards (inline in chat)
    ├── chat/
    │   ├── ChatScreen.kt           # Single chat screen composable
    │   ├── ChatViewModel.kt        # Chat state: StateFlow<List<ChatMessage>>
    │   └── ChatContent.kt          # Sealed class for message content types
    └── theme/
```

## Key Architectural Rules

### SOLID & OOP Principles
- **Encapsulation**: Hide internal state. Expose only what is necessary via public interfaces and immutable data structures (e.g., exposing `StateFlow` instead of `MutableStateFlow`).
- **Single Responsibility (SRP)**: Each class/function should have only one reason to change. Business logic, data fetching, and persistence MUST be separated.
- **Open/Closed (OCP)**: Code should be open for extension but closed for modification. Use interfaces and polymorphism instead of hardcoding specific implementations.
- **Liskov Substitution (LSP)**: Subclasses or implementations must be completely substitutable for their interfaces without breaking the app.
- **Interface Segregation (ISP)**: Keep interfaces small and focused. Clients should not be forced to depend on methods they do not use (e.g., separate DAOs per entity).
- **Dependency Inversion (DIP)**: Depend on abstractions, not concretions. Always use constructor injection via Hilt to provide dependencies.


### Layering
- **UI → ViewModel → Domain → Data** — never skip layers or go backwards.
- `ChatViewModel` orchestrates UI state but does not own business logic.
- `ChatBotAgent` (domain) owns the tool-call dispatch loop and trade lifecycle.
- `PortfolioRepository` (data) owns persistence — it is the only layer that writes to Room.

### Room Database
- `AppDatabase.kt` should contain only: the `@Database` annotation (entity list + version), and abstract DAO accessors. **No migration objects, no companion logic** — keep it minimal.
- Migrations go in a dedicated `DatabaseMigrations.kt` file, registered in `DatabaseModule`.
- We treat this as **production code** — never use `fallbackToDestructiveMigration()`.
- Bump `AppDatabase.version` and write a proper migration for every schema change.

### Price Cache Freshness
- `PriceCacheEntity` stores one row per ticker with the `tradingDate` (ISO string) derived from Yahoo's `regularMarketTime` field.
- **Yahoo is the source of truth** for trading dates — no hardcoded day-of-week or holiday logic.
- `PortfolioManager.resolvePrice()` always fetches Yahoo, then compares the returned `tradingDate` with the DB. If different → update DB. If same → skip write. Handles weekends/holidays automatically.

### DI (Hilt)
- All dependencies are scoped `@Singleton` in `SingletonComponent` unless there is a specific reason not to.
- Constructor injection (`@Inject`) everywhere — no service locator pattern.

### LLM / Tool Calling
- `ChatViewModel` only talks to the `LlmEngine` interface — never to a concrete provider implementation.
- Tool names are defined as constants in `ChatBotAgent` (no magic strings).
- Trade persistence is initiated from `ChatBotAgent`, not from `ChatViewModel`.

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3 |
| State | ViewModel + StateFlow |
| Database | Room (SQLite) |
| Networking | Ktor Client |
| Serialization | kotlinx.serialization |
| AI (BYOK) | Google GenAI SDK (`com.google.ai.client.generativeai`) |
| Charts | Vico |
| DI | Hilt |
| Testing | JUnit 4, MockK, Truth, Turbine, Ktor MockEngine, kotlinx-coroutines-test |

## Running the Project

```bash
./gradlew ktlintCheck            # Check Kotlin code style
./gradlew ktlintFormat           # Auto-fix most style violations
./gradlew testDebugUnitTest      # Run all unit tests
./gradlew assembleDebug          # Build debug APK
```

## Testing Guidelines

- Unit tests live in `app/src/test/java/com/chatfolio/`
- Use `MockEngine` (not MockK) for testing Ktor HTTP clients.
- Use `runTest` from `kotlinx-coroutines-test` for all suspend function tests.
- Financial calculation tests are the highest priority — results must match hand-verified values.
- Instrumented (Room) tests go in `app/src/androidTest/`.

## External API Notes

**Yahoo Finance** (`/v8/finance/chart/{ticker}?interval=1d&range=1d`)
- Unofficial, no auth required.
- Safe limit: ~50 calls/day per IP to stay well within rate limits.
- Key response fields: `meta.regularMarketPrice`, `meta.currency`, `meta.regularMarketTime` (epoch seconds of last close).
- `regularMarketTime` always reflects the last actual trading close — it does not advance on weekends or holidays.

**Gemini (Google GenAI SDK)**
- API key stored in `EncryptedSharedPreferences` via `SettingsRepository`.
- Key is never logged, never included in error messages, never sent to any server except Google's API endpoint.

## What's Not Here Yet

- `live-data-summary-card` — wiring `PortfolioManager` into `ChatViewModel` for live UI values
- `HoldingsTableCard`, `AllocationDonutCard` — upcoming UI cards
- CSV import pipeline
- Dividend tracking (auto + manual)
- WorkManager background price refresh
- Short Selling support (allowing negative share balances)
