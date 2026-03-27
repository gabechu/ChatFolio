# Product Requirements Document (PRD): ChatFolio Android

## 1. Product Vision

**ChatFolio Android** is a privacy-first, conversational investment analyst that lives entirely on your phone. Instead of complex dashboards and manual data entry, you simply *talk* to your portfolio. The AI understands your holdings, fetches live market data, and answers questions with rich visual cards — all while keeping your financial data local to your device.

**Core principles:**
- **Chat-first:** The chat interface *is* the app. No navigating between tabs and screens.
- **Local-first:** All portfolio data stays on-device in SQLite. Nothing goes to a developer's server.
- **Zero friction:** Add trades by telling the AI in plain English. Import history from CSV.

## 2. Target Audience

| Persona | Needs | Pain Points |
|---|---|---|
| **Retail investors** | Simple, private portfolio tracking | Existing tools are too complex or require cloud accounts |
| **Data-driven investors** | Ask natural language questions about performance | Spreadsheets are powerful but tedious |
| **Privacy-conscious investors** | No financial data shared with third-party servers | Most apps upload everything to their cloud |

## 3. Core Features — Phased MVP

### Phase 1: Chat + Manual Entry (MVP)

| Feature | Description |
|---|---|
| **Chat UI** | Single-screen conversational interface with text input |
| **AI Trade Entry** | User says "bought 100 VAS at $92.50" → Gemini function calling extracts structured data → user confirms → saved to Room |
| **Portfolio Summary Card** | Adaptive card showing total value, daily change, total return |
| **Holdings Card** | Inline card listing all positions with gain/loss |
| **Local Database** | Room/SQLite stores all transactions, holdings, and portfolio state |
| **BYOK AI Logic** | Users supply their own Gemini API key (Bring Your Own Key) using the `generativeai` SDK |
| **Settings Screen** | Base currency (AUD), data export, about |

### Phase 2: Market Data + CSV Import

| Feature | Description |
|---|---|
| **Live Prices** | Yahoo Finance EOD prices for ASX/US markets, cached locally (max 1 fetch/ticker/day) |
| **FX Rates** | AUD ↔ USD conversion for US holdings |
| **CSV Import** | Upload broker CSV → AI maps columns → user confirms → bulk import. Pre-built profile for IB Activity Statement |
| **Performance Chart Card** | Line chart of portfolio value over time (Vico library) |
| **Allocation Card** | Donut chart showing asset allocation |
| **Dividend Tracking** | Auto-fetch dividend history from Yahoo Finance + manual entry via chat |

### Phase 3: Advanced

| Feature | Description |
|---|---|
| **Photo OCR** | Snap a trade confirmation → Gemini Vision extracts details |
| **Broker API (IBKR)** | Direct sync via Interactive Brokers Client Portal API |
| **Portfolio Summaries** | AI-generated weekly digest of movers and news |
| **Local Backup/Restore** | Export/import encrypted JSON file for device migration |
| **Gemini Nano (On-Device)** | Basic offline AI for summaries when no network available |

## 4. Technical Constraints

| Component | Choice | Rationale |
|---|---|---|
| **Language** | Kotlin | Full-stack Android, Compose UI |
| **UI Framework** | Jetpack Compose + Material 3 | Modern declarative UI |
| **Database** | Room/SQLite (on-device) | Local-first, no server |
| **LLM Integration** | Google Generative AI SDK (BYOK) | Cost shifted to user free-tier, preventing unbounded developer bills |
| **Market Data** | Yahoo Finance (unofficial REST) | Free, per-IP rate limits (~250 calls/day safe) |
| **Charts** | Vico (Compose chart library) | Line + column charts for adaptive cards |
| **Currency** | AUD only (MVP) | Simplifies calculations |
| **Broker Priority** | Interactive Brokers first | CSV import + future API |

## 5. Data Ingestion Strategy

### Chat-Based Entry (Primary)

Uses **Gemini function calling** — no custom parsers needed:

1. User types natural language: "bought 50 AAPL at $178.50 on March 10"
2. Gemini receives message with declared `addTransaction` tool
3. Gemini returns `FunctionCall{ticker: "AAPL", action: "BUY", shares: 50, price: 178.50, date: "2026-03-10"}`
4. App shows confirmation card → user taps Save → written to Room

### CSV Import (Bulk)

1. User uploads CSV from broker export
2. App reads header + first 3 rows, sends to Gemini
3. Gemini returns column mapping: `{date: "Trade Date", ticker: "Symbol", ...}`
4. User confirms mapping → app parses all rows → bulk insert to Room
5. Mapping saved as "broker profile" for future imports

**Pre-built profile:** Interactive Brokers Activity Statement CSV format.

### Dividend Tracking

- **Automatic:** Yahoo Finance provides dividend history (amounts + ex-dates) per ticker
- **Manual:** User adds via chat: "received $50 dividend from VAS on March 1"

## 6. Success Metrics

| Metric | Target | How Measured |
|---|---|---|
| **Calculation accuracy** | Zero discrepancy vs manual spreadsheet | Automated test suite against known portfolios |
| **Chat engagement** | ≥ 3 chat interactions per session | Analytics event logging |
| **Onboarding speed** | First trade entered within 60 seconds | Time from app launch to first saved transaction |
| **Data privacy** | Zero user data sent to non-Google servers | Code audit + network traffic analysis |

## 7. Testing Strategy

| Layer | Approach |
|---|---|
| **Financial calculations** | Unit tests for TWR, IRR, cost-basis, gain/loss against known spreadsheet values |
| **Gemini function calling** | Integration tests with recorded prompts verifying correct `FunctionCall` extraction |
| **CSV parsing** | Unit tests with sample IB Activity Statement CSVs |
| **Room database** | Instrumented tests for CRUD operations, migrations, data integrity |
| **UI** | Compose UI tests for card rendering and chat flow |
| **End-to-end** | Manual test scenarios: add trade → verify portfolio → check performance |

## 8. Cost Model

| Service | Free Tier | Expected Usage |
|---|---|---|
| **Google Gemini API (BYOK)** | Google AI Studio free tier (User provides key) | ~50-100 chat messages/day per user |
| **Yahoo Finance** | Unlimited (unofficial, per-IP) | ~50 price fetches/day |
| **Firebase** | REMOVED | 0 (App is 100% local and disconnected from developer servers) |

**Total infrastructure cost for MVP: $0**

## 9. Future Roadmap

- Multi-asset support (crypto, property)
- Multi-currency (NZD, USD, GBP)
- Tax reporting (CGT for Australian investors)
- Additional broker CSV profiles (CommSec, SelfWealth)
- Gemini Nano fully offline mode
