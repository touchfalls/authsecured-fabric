# Changelog - AuthSecured v1.0.3 (Fabric 26.2)

🚀 **AuthSecured 1.0.3** - Enterprise-Grade Fabric Server Authentication Mod for **Minecraft 26.2**.

---

## 🔥 IMPORTANT BUG FIXES & COMPATIBILITY UPDATES

- 🎯 **Full Minecraft 26.2 Support**: Project build target, dependencies, Fabric API (`v0.156.0+26.2`), and mod manifest (`fabric.mod.json`) fully updated to **Minecraft 26.2**.
- 🛠️ **IDE & Project Structure Fix**: Configured workspace settings (`.vscode/settings.json`, `.project`, `.classpath`) resolving all `non-project file` and unresolved import warnings across all modules.
- 🛡️ **Hardened Password Hash Verification**: Fixed potential server exception in `PasswordHasher.verify` when parsing corrupted or non-standard hash strings in the database.
- 🧹 **Active Memory Leak Protection**: Added `cleanupExpired()` purging logic to `LocalMemoryRateLimiter` to automatically remove stale IP/user rate-limit tracking records during high uptime.
- ⚙️ **Clean 0-Error Build System**: Embedded custom build environment producing clean, standalone fat-jars (`authsecured-1.0.3.jar`) ready for production servers.

---

## 🌟 Full Feature Overview

### 🛡️ Enterprise Security & Password Protection
- **Argon2id Password Hashing**: OWASP-compliant Argon2id hashing using `Password4j` (64MB memory cost, 3 iterations, 1 parallelism).
- **Strict Memory Safety**: Active zeroing of sensitive `char[]` arrays in memory (`PasswordHasher.wipe(...)`) to prevent heap dump leaks.
- **IP Anonymization**: Automatic SHA-256 IP masking for privacy compliance.

### 💾 Multi-Database & Hybrid Storage Architecture
- **Dual Database Backends**: Support for local **SQLite** (file-based) and production **PostgreSQL** with `HikariCP` connection pooling.
- **Automated Schema Migrations**: Integrated **Flyway** migration engine for automatic database schema creation and updates.
- **Redis & Memory Hybrid Session Management**: Fast in-memory session cache backed by distributed **Redis** storage with TTL session expiry support.

### ⚡ Sliding Window Rate Limiting
- **Brute-Force Protection**: Hybrid sliding window rate limiter tracking IP addresses and usernames with temporary lockouts.

### 🔒 Player Restrictions & Mixin Security
- **Strict Pre-Auth Restrictions**: Movement, chat, command, inventory drop/pickup, block break/place, and damage restrictions for unauthenticated players via Fabric Mixins.

### 💬 Commands & Localization
- **Player Commands**: `/register`, `/login`, `/changepassword`, `/logout`
- **Admin Commands**: `/auth status`, `/auth unregister`, `/auth unlock`
- **Multi-Language**: Full localization in English (`en.json`), Russian (`ru.json`), Spanish (`es.json`), French (`fr.json`), and Italian (`it.json`).

---
*Developed for Fabric 26.2 and Java 21+.*
