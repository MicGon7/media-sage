# MS-202: Structured JSON Logging for :agent via Logback + SLF4J

## What changed

The `:agent` module switched from `java.util.logging` (JUL) to SLF4J backed by Logback with `logstash-logback-encoder`. Every log line is now a JSON object that Cloud Logging indexes as `jsonPayload` — queryable by `level`, `message`, `logger`, and any structured fields.

## Why

JUL writes plain text to stdout. Cloud Run wraps this in `textPayload`, which is unstructured and unsearchable in the GCP Log Explorer. With LogstashEncoder each line becomes a JSON object:

```json
{
  "timestamp": "2026-05-20T20:00:00.000Z",
  "level": "INFO",
  "logger_name": "com.mediasage.agent.service.AgentLaunchService",
  "message": "[MS-202] agent launched (pid 1234)"
}
```

Cloud Logging automatically promotes these to `jsonPayload`, making every field filterable in the Log Explorer with queries like:

```
jsonPayload.level="ERROR"
jsonPayload.logger_name="com.mediasage.agent.service.CloudRunJobsClient"
```

## Changes made

### `gradle/libs.versions.toml`
Added `logstash-logback-encoder = "8.0"` version and library entry. Version 8.x is required for Logback 1.5.x compatibility (7.x targets Logback 1.2–1.4).

### `agent/build.gradle.kts`
Added `implementation(libs.logstash.logback.encoder)`. Logback classic was already a dependency.

### `agent/src/main/resources/logback.xml`
Replaced the text pattern encoder with `LogstashEncoder`:

```xml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

### All `:agent` Kotlin files
Replaced `java.util.logging.Logger` with SLF4J across 7 files:
- `AgentLaunchService.kt`
- `CloudRunJobsClient.kt`
- `AgentBriefing.kt`
- `JiraApiService.kt`
- `AgentModule.kt`
- `JiraWebhookRoutes.kt`
- `GitHubWebhookRoutes.kt`

Key API differences:
- `Logger.getLogger(Foo::class.java.name)` → `LoggerFactory.getLogger(Foo::class.java)`
- `log.warning(msg)` → `log.warn(msg)` (SLF4J uses `warn`, not `warning`)
- Private top-level functions that accepted `log: java.util.logging.Logger` as a parameter had their type updated to `org.slf4j.Logger`

## Pattern to follow

For any new class in `:agent` that needs logging:

```kotlin
import org.slf4j.LoggerFactory

class MyService {
    private val log = LoggerFactory.getLogger(MyService::class.java)
}
```

For module-level or function-level loggers (routes files):

```kotlin
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("MyRoutesLogger")
```

## Logstash encoder version compatibility

| logstash-logback-encoder | Logback |
|---|---|
| 7.x | 1.2–1.4 |
| 8.x | 1.4+ (including 1.5.x) |

This project uses Logback 1.5.18, so 8.0 is the correct minimum.
