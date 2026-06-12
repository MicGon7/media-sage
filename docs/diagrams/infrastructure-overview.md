# Infrastructure Overview

```mermaid
graph TB
    subgraph Clients["Mobile Clients"]
        Android["Android App"]
        iOS["iOS App"]
    end

    subgraph KMP["Kotlin Multiplatform Modules"]
        composeApp[":composeApp<br/>Compose Multiplatform UI"]
        shared[":shared<br/>Business logic · Room · Ktor Client"]
        server[":server<br/>Ktor API Server"]
        agent[":orchestrator<br/>Orchestrator Server"]
    end

    subgraph Railway["Railway"]
        AppAPI["App API<br/>:server · port 8080"]
    end

    subgraph GCP["GCP"]
        Orchestrator["Cloud Run Service<br/>media-sage-orchestrator<br/>:orchestrator · port 8081"]
        subgraph Jobs["Cloud Run Jobs"]
            WorkerJob["worker<br/>Dockerfile.worker<br/>Claude Code + Android SDK"]
            LiteJobs["judge · comment · conflict<br/>Dockerfile.lite<br/>Claude Code only"]
        end
        PubSub["Pub/Sub<br/>cloud-run-job-completions"]
    end

    subgraph Data["Data"]
        Supabase["Supabase Postgres<br/>jobs table · dedup registry"]
        Room["Room DB<br/>on-device cache"]
    end

    subgraph External["External APIs"]
        Claude["Anthropic Claude API<br/>(via Fuelix proxy)"]
        NewsAPI["The News API"]
        Scripture["Scripture API"]
        JiraGH["Jira + GitHub<br/>webhooks + MCP"]
    end

    Android --> composeApp
    iOS --> composeApp
    composeApp --> shared
    shared --> Room
    shared -->|HTTP| AppAPI
    server --> AppAPI
    AppAPI --> Claude
    AppAPI --> NewsAPI
    AppAPI --> Scripture

    agent --> Orchestrator
    Orchestrator -->|dispatch worker job| WorkerJob
    Orchestrator -->|dispatch lite job| LiteJobs
    Orchestrator --> Supabase
    WorkerJob -->|completion event| PubSub
    LiteJobs -->|completion event| PubSub
    PubSub -->|push webhook| Orchestrator
    WorkerJob --> Claude
    LiteJobs --> Claude
    WorkerJob -->|git + gh CLI| JiraGH
    LiteJobs -->|git + gh CLI| JiraGH
    Orchestrator -->|post Jira comment| JiraGH
```

## Module responsibilities

| Module | Runtime | Deployed to |
|--------|---------|-------------|
| `:composeApp` | Android + iOS | App stores |
| `:shared` | Android + iOS | Bundled with app |
| `:server` | JVM (Netty, port 8080) | Railway |
| `:orchestrator` | JVM (Netty, port 8081) | GCP Cloud Run Service |

## Data flow (product)

Room is the single source of truth for the app. The UI always reads from Room via Flow.
Network calls update Room.

```
Server JSON → Client DTO → Room Entity → Domain Model → UI
```

## Agent infrastructure

The orchestrator is a long-running Cloud Run **Service** (always on, min 1 instance).
Workers are Cloud Run **Jobs** — spun up on demand per ticket, run to completion, then torn down.
Supabase persists the job registry across orchestrator restarts.
