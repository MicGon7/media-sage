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
- **Use cases** (`domain/usecase/`) — single-purpose classes with one `operator fun invoke()`. Follows Now in Android.

## Use cases (domain layer)

The domain layer is **optional and added only when needed** — do not create a use case for every
repository call. Add one when either applies (NiA's rule):
- A ViewModel needs to **combine or transform data from more than one repository** into a single
  stream. Extract the `combine` into a use case that returns a domain model (e.g.
  `GetReaderCalendarUseCase` → `Flow<ReaderCalendarData>`), so the ViewModel receives one stream, not five.
- The same logic is **reused across multiple ViewModels**.

Rules:
- Name `VerbNounUseCase`; expose a single `operator fun invoke(...)`.
- Use cases **read/combine** data. They do **not** handle events — bookmarking, saving, assigning,
  etc. are events the ViewModel sends straight to a repository. No `SetXUseCase` wrappers.
- Keep them pure-domain: no UI/presentation types, no Android/Compose imports. Mapping domain data
  into displayable UI models stays in the ViewModel.
- A single-repository pass-through with no combining or transforming does **not** warrant a use case —
  the ViewModel calls the repository directly.

## Conventions

- `@SerialName` annotations on their own line above the property
- Room schemas stored in `shared/schemas/` — committed to version control
- Use `kotlinx.serialization` for all JSON — no Gson/Moshi
- Use Ktor client in shared, never Ktor server
- Platform HTTP engines: OkHttp (Android), Darwin (iOS) — wired in `HttpClientFactory`

## Dependency Injection

`sharedModule(serverBaseUrl)` — wires HttpClient, MediaSageApi, and all repositories. Define modules per feature, not per layer.
