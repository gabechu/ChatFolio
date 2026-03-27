# ChatFolio 🤖💼

**A local-first, privacy-focused AI investment analyst that lives entirely on your Android device.**

![Kotlin](https://img.shields.io/badge/Kotlin-Native-purple.svg)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg)
![Room Database](https://img.shields.io/badge/Room-SQLite-green.svg)

ChatFolio abandons the bloated dashboards of traditional finance apps. Instead, the interface is a **single, unified chat screen**. You talk to your portfolio in natural language, and the AI responds with rich adaptive cards, live charts, and accurate financial calculus.

## 🛡️ Architecture & Privacy
**Your financial data never leaves your device.** 
1. **Local-First:** All transactions, holdings, and portfolio metrics are stored strictly on-device using Jetpack **Room (SQLite)**. 
2. **Bring Your Own Key (BYOK):** The core intelligence runs via the `google-genai` SDK. You provide your own Gemini API key, which is encrypted locally within Android's `EncryptedSharedPreferences`.
3. **Zero Developer Backend:** There are no developer servers, no user accounts, and no data harvesting. The app communicates directly from your phone to Google servers via the API.

## ✨ Features
* **Natural Language Trade Entry:** Just type *"I bought 10 shares of TSLA at $200 and sold 5 AAPL at $150."*
* **Batch Shopping Cart UX:** The Gemini model automatically parses complex, multi-trade paragraphs into parallel function calls, presenting you with a single "receipt" card for one-click database insertion.
* **Instant Portfolio Recalculation:** The Room database instantly cascades every saved transaction into aggregated underlying Holding entities, recalculating your Total Cost Basis and Position Sizes instantly.
* **Adaptive Cards:** Chat content is dynamically rendered into rich Jetpack Compose UI elements rather than raw markdown.

## 🛠️ Tech Stack
* **UI:** Jetpack Compose, Material 3 
* **State Management:** MVVM, Kotlin StateFlow
* **Database:** Room
* **Dependency Injection:** Hilt
* **AI:** Google Generative AI SDK (Gemini 2.5 Flash)

## 🚀 Getting Started
1. Clone the repository.
2. Open the project in Android Studio.
3. Build and deploy to your emulator or physical Android device.
4. On the very first launch, the app will automatically prompt you to insert your Google AI Studio API Key. 

---
*Built with ❤️ using Android Clean Architecture.*
