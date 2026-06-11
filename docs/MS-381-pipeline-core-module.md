# MS-381: Extract job schema and pipeline event models into :pipeline-core module

## What was built

A new `:pipeline-core` Gradle module that holds the job persistence layer and Pub/Sub event
models for the autonomous agent pipeline. These types were extracted from `:agent` so they can
be reused by future modules (e.g. `:feedback`) without pulling in the orchestrator's Ktor server,
Koin wiring, or Cloud Run dispatch logic.

## What moved

| Type | Old location | New location |
|---|---|---|
| `JobsTable` | `agent/db/JobsTable.kt` | `pipeline-core/.../JobsTable.kt` |
| `JobStatus`, `JobRow`, `JobDurationRow`, `JobRepository` | `agent/db/JobRepository.kt` | `pipeline-core/.../JobRepository.kt` |
| `JobRegistry` | `agent/db/JobRegistry.kt` | `pipeline-core/.../JobRegistry.kt` |
| `WorkerMetrics` | `agent/db/WorkerMetrics.kt` | `pipeline-core/.../WorkerMetrics.kt` |
| `JobCompletionEvent` | private in `agent/routes/PubSubWebhookRoutes.kt` | `pipeline-core/.../JobCompletionEvent.kt` |

`AgentDatabase.kt` stays in `:agent` — it wires the Exposed connection and is tightly coupled
to the orchestrator's startup lifecycle.

## Module structure

```
pipeline-core/
├── build.gradle.kts              — JVM-only; Exposed + kotlinx-serialization + coroutines only
└── src/main/kotlin/com/mediasage/pipeline/core/
    ├── JobsTable.kt
    ├── JobRepository.kt          — includes JobStatus, JobRow, JobDurationRow, JobRepository
    ├── JobRegistry.kt
    ├── WorkerMetrics.kt
    └── JobCompletionEvent.kt
```

## Design decisions

**No Ktor, no Koin.** The module's only dependencies are Exposed (SQL), kotlinx-serialization
(JobCompletionEvent), and kotlinx-coroutines (JobRepository uses `withContext(Dispatchers.IO)`).
This keeps the module portable — any project can include it with a single Gradle `include` line
and a Postgres connection.

**`JobCompletionEvent` promoted from private to public.** It was `private` inside
`PubSubWebhookRoutes.kt` because it was only consumed locally. After extraction, other modules
that subscribe to the Pub/Sub topic can deserialize the same event shape without redefining it.

**Package is `com.mediasage.pipeline.core`, not `com.mediasage.agent.db`.** The new package
signals that this layer belongs to the pipeline pattern, not the Media Sage orchestrator
specifically. Future portability work (MS-262) aims to make the pipeline reusable across clients.

## How :agent depends on :pipeline-core

`agent/build.gradle.kts` adds:
```kotlin
implementation(projects.pipelineCore)
```

All call sites in `:agent` updated their imports from `com.mediasage.agent.db.*` to
`com.mediasage.pipeline.core.*`. No logic was changed.

## Pre-existing bug fixed

`GitHubWebhookRouteTest.kt` had a fake `launchForJudge` override with the wrong signature
(missing the `prNumber: Int?` parameter added in a prior ticket). The mismatch was caught by
our `compileTestKotlin` step and fixed in this PR.
