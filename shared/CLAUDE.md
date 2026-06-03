# shared — KMP Library

## Data Flow

Room is the single source of truth. The UI always reads from Room via Flow. Network calls update Room.

```
Server JSON → Client DTO → Room Entity → Domain Model → UI
```

## Layers

- **DTOs** (`data/remote/`) — server response shapes, `@Serializable`, serialization only. No business logic.
- **Entities** (`data/local/entity/`) — Room database schema. Annotated with `@Entity`, `@PrimaryKey`, etc.
- **Domain Models** (`domain/model/`) — clean types for UI (enums, lists). No framework annotations.
- **Repositories** (`data/repository/`) — bridge all three layers. Interface in `domain/repository/`, implementation in `data/repository/`.
- **Mappers** (`data/mapper/`) — pure functions converting Entity ↔ Domain Model. No side effects.

## Conventions

- `@SerialName` annotations on their own line above the property
- Room schemas stored in `shared/schemas/` — committed to version control
- Use `kotlinx.serialization` for all JSON — no Gson/Moshi
- Use Ktor client in shared, never Ktor server
- Platform HTTP engines: OkHttp (Android), Darwin (iOS) — wired in `HttpClientFactory`

## Dependency Injection

`sharedModule(serverBaseUrl)` — wires HttpClient, MediaSageApi, and all repositories. Define modules per feature, not per layer.
