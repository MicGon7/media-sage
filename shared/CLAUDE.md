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

## Encrypting a field at rest — device-local key (single device only)

`ReflectionNoteCipher` (`data/crypto/`) is the reference pattern for encrypting a value with a
**non-exportable, device-local key** — established in MS-737. This guarantees "only this device
can read this," which is a fundamentally different (stronger, but non-portable) guarantee than the
account-shared-key pattern below. Do not reach for this pattern for anything that needs to sync
across a signed-in user's devices — see MS-740, which had to walk this back for exactly that reason.
- A common `interface` with `encrypt`/`decrypt`, plus an `expect fun create...(): Interface`
  factory — no `Context`/setup object needed since both platforms' key stores (Android Keystore,
  iOS Keychain) are reachable with no arguments.
- Android: a non-exportable AES-256-GCM key generated in `AndroidKeyStore` via
  `KeyGenParameterSpec`; `javax.crypto.Cipher` does the encrypt/decrypt.
- iOS: an EC key pair generated in the Keychain via `SecKeyCreateRandomKey`; encrypt/decrypt goes
  through `SecKeyCreateEncryptedData`/`SecKeyCreateDecryptedData` with
  `kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM` (AES-GCM under the hood, reached via
  Apple's `Security` framework — no CryptoKit, no CommonCrypto cinterop, no new dependency).
- The `actual` implementations get no unit tests (Keystore/Keychain aren't available in
  `commonTest`); test the repository's encrypt/decrypt orchestration with a `Fake...Cipher` instead.

In `UserReflectionNoteRepositoryImpl`, `ReflectionNoteCipher` is now repurposed narrowly: it only
wraps/unwraps the *cached copy* of the shared account key (below) for local at-rest storage. It no
longer encrypts the note text directly.

## Encrypting a field at rest — shared account key (syncs across devices)

`SharedNoteCipher` (`data/crypto/`) + the account-key provisioning in
`UserReflectionNoteRepositoryImpl` is the reference pattern when a field must be both encrypted at
rest *and* readable on every device a user signs into — established in MS-740, after MS-736/MS-737
shipped a device-local key that made cross-device sync silently undecryptable everywhere but the
originating device.
- One AES-256 key is generated **per account**, the first time any device needs it, and stored in
  a dedicated RLS-protected Supabase table (`auth.uid() = user_id`) — never exposed to any other
  account. `ReflectionNoteKeyRemoteDataSource.push` is a plain `insert` (not `upsert`): the
  table's primary key on `user_id` is what makes "exactly one device wins the provisioning race"
  a real database guarantee, not an application-level assumption.
- Every device fetches this key once and caches it locally via `LocalAccountKeyDao`, wrapped
  through the device-local `ReflectionNoteCipher` above before it ever touches Room — the local
  database still only ever holds ciphertext (the shared key's wrapped copy, and the note's
  ciphertext), never the raw key or plaintext note.
- `SharedNoteCipher` is pure `commonMain` — no `expect`/`actual` needed — using the
  `dev.whyoleg.cryptography` KMP library (`cryptography-core` + `cryptography-provider-optimal`,
  which resolves to JCA on Android and CryptoKit on iOS internally). This is the first crypto
  dependency in this codebase; it exists because Apple's native `Security` framework (used by
  `ReflectionNoteCipher` above) has no clean way to run AES-GCM with an arbitrary caller-supplied
  key, which a *shared* key fundamentally requires.
- Back-compat: a note encrypted before MS-740 (or before this device had ever provisioned the
  account key) falls back to `ReflectionNoteCipher.decrypt` directly on read, and migrates onto the
  shared key the next time it's saved. It is only ever readable that way on the original device
  that wrote it — the whole point of MS-740 was that this fallback is not a substitute for a
  portable key.
- Test the provisioning race (two `Fake...RemoteDataSource` clients backed by the same in-memory
  map, one `push` throwing on conflict) and a genuine two-"device" round trip (two repository
  instances with separate `Fake` local DAOs sharing only the remote fakes) — a single shared fake
  cipher across "devices" cannot catch a key-portability bug, since it can't fail the way two truly
  different keys would.
