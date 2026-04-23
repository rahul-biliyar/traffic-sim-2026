# Contributing

## Prerequisites

- Java 17+, Node.js 20+
- An IDE with Kotlin/Gradle support (IntelliJ IDEA recommended for the server modules)

## Running locally

```bash
# Terminal 1 — server with auto-reload
./gradlew :server:run --continuous

# Terminal 2 — client with HMR
cd client && npm install && npm run dev
```

## Code style

- **Java**: standard Micronaut conventions, no wildcard imports beyond `model.*` and `systems.*`
- **TypeScript**: strict mode enabled; avoid `any`
- **Commits**: use the conventional-commits format (`feat:`, `fix:`, `refactor:`, `docs:`)

## Module responsibilities

| Module   | Purpose                                                  |
| -------- | -------------------------------------------------------- |
| `engine` | Pure simulation primitives — no game-specific logic      |
| `game`   | All game domain logic; depends on `engine` only          |
| `shared` | Serialisable protocol records; no runtime dependencies   |
| `server` | Micronaut wiring, WebSocket protocol, session management |
| `client` | Three.js rendering, input handling, network client       |

## Submitting changes

1. Fork the repository
2. Create a branch: `git checkout -b feat/your-feature`
3. Make your changes and run `./gradlew build -x test` to verify the server compiles
4. Run `cd client && npm run build` to verify the client compiles
5. Open a pull request — describe what changed and why

## Reporting bugs

Open a GitHub Issue and include:

- Steps to reproduce
- Expected vs. actual behaviour
- Browser + OS + Java version
