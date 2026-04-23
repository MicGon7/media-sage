# MS-55: Mock Data Mode for Offline Demos

## What Changed
Added a mock data mode that allows the app to run without a server connection. Useful for physical device demos when the server isn't reachable.

## How to Toggle

In `local.properties` (not version controlled):

```properties
# Demo mode — no server needed
use.mock.data=true

# Live mode (default)
use.mock.data=false
```

Rebuild after changing the flag — it's a compile-time BuildConfig value.

## How It Works

1. `build.gradle.kts` reads `use.mock.data` from `local.properties`, defaults to `false`
2. Generates `BuildConfig.USE_MOCK_DATA` boolean
3. `MediaSageApplication` checks the flag at startup
4. If true, loads `mockApiModule` which overrides `MediaSageApi` with `MockMediaSageApi`
5. All API calls return realistic mock data instead of hitting the server

```
local.properties → build.gradle.kts → BuildConfig → MediaSageApplication → Koin module swap
```

## Mock Data

### Headlines
5 headlines with Unsplash image URLs covering society, health, politics, environment, education.

### Encourage Results
Each headline has a unique match with different:
- Christian figure (Calvin, Elliot, MLK, Francis, Lewis)
- Quote text
- Scripture reference and full verse text
- Explanation
- Tone (all EXHORTATION in mock — covers the common case)

## Files

| File | Purpose |
|------|---------|
| `di/MockConfig.kt` | `useMockData` flag object |
| `di/MockData.kt` | Headlines and encourage results |
| `di/MockMediaSageApi.kt` | Mock API implementation |
| `di/AppModule.kt` | `mockApiModule` definition |
| `MediaSageApplication.kt` | Conditional module loading |
| `build.gradle.kts` | `USE_MOCK_DATA` BuildConfig field |

## Future
MS-54 will migrate this to Android product flavors for a cleaner developer experience.
