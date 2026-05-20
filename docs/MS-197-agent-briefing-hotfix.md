# MS-197 — AgentBriefing Silent Failure & Pipe Deadlock Hotfix

## Problems Fixed

### 1. Silent Failure (No Diagnostic Output)
`AgentBriefing.prepare()` logged "exited with code 1" but swallowed all process output.
Since `redirectErrorStream(true)` merges stdout and stderr, the actual `claude` error message
was being discarded. Added logging of the first 500 chars of output on non-zero exit so the
cause is visible in orchestrator logs.

### 2. Pipe Buffer Deadlock Risk
The original code called `process.inputStream.bufferedReader().readText()` **after** `waitFor()`.
This is safe only if the process output fits in the OS pipe buffer (~64KB). If `claude` produces
more output than that, the process blocks on write, `waitFor()` never returns, and the
180s timeout eventually fires — masking the real error as a timeout.

**Fix:** Read the output stream on a separate thread (`Executors.newSingleThreadExecutor()`)
concurrently with `waitFor()`. The thread drains the pipe so the process can write freely,
while the main thread enforces the timeout.

```kotlin
val executor = Executors.newSingleThreadExecutor()
val outputFuture = executor.submit<String> {
    process.inputStream.bufferedReader().readText()
}
executor.shutdown()

val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
// ...
val output = outputFuture.get().trim()
```

### 3. Worker Image Architecture Mismatch
The worker Docker image was previously pushed from an Apple Silicon Mac without `--platform`,
defaulting to `arm64`. Cloud Run requires `linux/amd64` and rejects arm64 images with:
```
Container manifest type 'application/vnd.oci.image.index.v1+json' must support amd64/linux
```

**Fix:** Always build with `--platform linux/amd64`:
```bash
docker build --platform linux/amd64 -f Dockerfile.worker -t <image>:latest .
```

## Key Learnings

- **Always log process output on failure.** Swallowing stderr/stdout makes subprocess errors
  undiagnosable from the orchestrator logs.
- **Pipe buffer deadlock pattern:** Reading `process.inputStream` after `waitFor()` is only
  safe for small outputs. For any process that might produce significant output, always drain
  the stream concurrently on a separate thread.
- **Cross-platform Docker builds:** On Apple Silicon, `docker build` defaults to `arm64`.
  Cloud Run and most Linux server environments require `linux/amd64`. Always specify
  `--platform linux/amd64` when building images destined for GCP.
- **MS-196** (auto-rebuild worker image via GitHub Actions) will eliminate the manual
  `--platform` concern by building in CI on standard `ubuntu-latest` runners (amd64).
