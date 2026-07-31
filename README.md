# SheeGlam — Salon Payment & Commission Manager

**SheeGlam** is a modern Android application built for salon and beauty professionals to easily track employee payments, service ledgers, commissions, and revenue analytics. It seamlessly synchronizes data directly from a Google Sheet (such as a Google Form Service Ledger) without requiring complex backend setups or API credentials.

---

## ✨ Key Features

* 📊 **Google Sheets Sync (No API Key Required)**: Easily sync service records directly from your Google Sheet using standard public read permissions ("Anyone with the link can view").
* 💰 **Commission & Dues Tracking**: Automatically calculates total earnings, commissions earned, and outstanding amounts owed to each team member ("Handled By").
* 📈 **Revenue & Analytics Dashboard**: Visual breakdown of total salon revenue, total paid out, pending dues, and staff performance metrics.
* 💳 **Payment Status Management**: Mark payments as completed or pending, with local history tracking to manage staff payouts accurately.
* 📱 **Offline Access & Room Persistence**: Built-in local SQLite database (via Android Room) allows viewing history, reports, and staff ledgers offline.
* 🎨 **Material 3 UI**: Clean, intuitive visual design built with Jetpack Compose, supporting dark and light themes.

---

## 🚀 How to Set Up Your Google Sheet

SheeGlam connects to your Google Sheet's **Service Ledger** tab to fetch transaction details. Follow these quick steps to connect your sheet:

1. **Prepare Your Google Sheet**:
   * Ensure your sheet contains a worksheet/tab named **Service Ledger** (or connected to your salon's Google Form response sheet).
   * Ensure standard column headers are present (e.g., `Handled By`, `Amount Paid`, `Service`, `Date`, etc.).

2. **Set Public Sharing Permissions**:
   * Open your Google Sheet in a web browser.
   * Click **Share** in the top right corner.
   * Under *General Access*, change the permission from *Restricted* to **"Anyone with the link can view"**.
   * Copy the share link or Sheet ID.

3. **Sync in SheeGlam**:
   * Open the **SheeGlam** Android app.
   * Paste the Google Sheet URL into the app's sync field.
   * Tap **Sync Now** to pull the latest transactions and update staff commissions and dues.

---

## 🛠️ Technical Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose with Material Design 3 (M3)
* **Architecture**: MVVM (Model-View-ViewModel) pattern
* **Data Persistence**: Room Database for offline storage & state synchronization
* **Networking**: OkHttpClient for resilient multi-method CSV/TSV data fetching
* **Asynchrony**: Kotlin Coroutines & StateFlow for reactive UI updates

---

## 🔒 Privacy & Data Flow

* **No Credentials Stored**: The app requires **no Google API keys, passwords, or OAuth tokens**.
* **Direct Connection**: All data is fetched directly from your shared Google Sheet URL to your device.
* **Local Storage**: Transaction records and payment updates are persisted locally on your Android device using Room DB.
