# 🛡️ AuthSecured Fabric Mod

**AuthSecured** is an enterprise-grade, high-performance authentication mod for **Fabric 26.2** Minecraft dedicated servers. Built with clean hexagonal architecture, zero-dependency leaks between modules, Argon2id encryption, and hybrid storage capabilities (SQLite, PostgreSQL, Redis).

---

## 🚀 Key Features

- **🔐 OWASP-Compliant Security**: Argon2id password hashing with memory array wiping (`char[]` zeroing) for maximum protection against heap inspection attacks.
- **⚡ Dual Database & Migrations**: Local SQLite file storage or production-grade PostgreSQL with HikariCP connection pooling and automatic Flyway schema migrations.
- **🔄 Hybrid Session & Redis Caching**: Fast in-memory session management paired with optional distributed Redis cluster support for multi-proxy networks.
- **⏱️ Sliding Window Rate Limiter**: Intelligent brute-force protection locking out abusive IPs and usernames dynamically.
- **🚫 Pre-Authentication Restrictions**: Complete server-side movement, chat, command, damage, and interaction blocking for unauthenticated players via optimized Fabric Mixins.
- **🌐 Multi-Language i18n Localization**: Built-in support for English (`en`), Russian (`ru`), Spanish (`es`), French (`fr`), and Italian (`it`).

---

## 📂 Project Architecture

The project is structured as a multi-module Gradle repository:

```
authsecured-fabric/
├── core/                  # Core Hexagonal Architecture & Ports
│   ├── src/main/java/
│   │   └── net/authsecured/core/
│   │       ├── db/        # Database Service (HikariCP, Flyway)
│   │       ├── limiter/   # Rate Limiters (LocalMemory, Redis, Hybrid)
│   │       ├── model/     # Domain Models (UserAccount, UserSession)
│   │       ├── port/      # Core Ports (AuthRepository, SessionStorage)
│   │       ├── repository/# SQL Storage Implementations
│   │       ├── security/  # PasswordHasher (Argon2id), IpAnonymizer
│   │       └── session/   # Hybrid Session Stores
├── fabric/                # Fabric Mod Implementation
│   ├── src/main/java/
│   │   └── net/authsecured/fabric/
│   │       ├── auth/      # AuthManager & Session Controller
│   │       ├── command/   # Command Registrations (/register, /login, etc.)
│   │       ├── i18n/      # Message Service & Translations
│   │       └── mixin/     # Fabric Mixins (Movement, Items, Combat, Interactions)
└── build/libs/            # Built Mod Artifacts (.jar)
```

---

## 🛠️ Commands & Permissions

### Player Commands
| Command | Usage | Description |
| :--- | :--- | :--- |
| `/register` | `/register <password> <confirmPassword>` | Registers a new account |
| `/login` | `/login <password>` | Logs in to an existing account |
| `/changepassword` | `/changepassword <oldPassword> <newPassword>` | Changes your password |
| `/logout` | `/logout` | Terminates active session |

### Admin Commands (Permission Level 4)
| Command | Usage | Description |
| :--- | :--- | :--- |
| `/auth status` | `/auth status [player]` | Checks player authentication status |
| `/auth unregister` | `/auth unregister <player>` | Force unregisters a player account |
| `/auth unlock` | `/auth unlock <player>` | Unlocks rate-limited player/IP |

---

## 📦 Installation & Setup

1. Make sure your server is running **Fabric 26.2** with **Fabric API** installed in the `mods/` directory.
2. Download `authsecured-1.0.3.jar` from the releases or build artifacts.
3. Place `authsecured-1.0.3.jar` into the `mods/` directory of your Fabric server.
4. Start the server.

---

## 🏗️ Building from Source

Requires **JDK 21+**.

```bash
# Clone the repository
git clone https://github.com/authsecured/authsecured-fabric.git
cd authsecured-fabric

# Build all module artifacts (.jar)
./gradlew build
```

Built binaries will be located in:
- `build/libs/authsecured-1.0.3.jar`
- `fabric/build/libs/authsecured-1.0.3.jar`

---

## 📜 License

Distributed under the **MIT License**. See `LICENSE` for more information.
