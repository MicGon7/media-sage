# appServer — Ktor API Server

## Structure

```
appServer/src/main/kotlin/com/mediasage/appserver/
├── Application.kt       — Entry point, Koin setup
├── plugins/             — ContentNegotiation, CORS, CallLogging, StatusPages
├── routes/              — Health, News, Encourage, Scripture, Figures, DailyReflection
├── service/             — ClaudeApiService, NewsApiService, ScriptureApiService
└── di/                  — ServerModule
```

## Conventions

- JVM-only Ktor server (Netty, port 8080). Never import Ktor client here — that lives in `:shared`.
- `serverModule(claudeApiKey, newsApiKey, scriptureApiKey, baseUrl)` wires HttpClient and all API services via Koin.
- API keys read from `application.conf` via environment variables — never hardcoded.
- Routes are thin: parse the request, call a service, return the response. No business logic in route handlers.
- StatusPages plugin handles all error mapping — do not catch and re-throw in routes.
- Deployed to Railway (port 8080). Requires manual restart — no hot-reload. Verify the server is running before debugging route behavior.
