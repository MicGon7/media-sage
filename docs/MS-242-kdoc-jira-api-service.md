# MS-242: Add KDoc to JiraApiService public methods

## What changed

Added KDoc comments to `agent/src/main/kotlin/com/mediasage/agent/service/JiraApiService.kt`.

**Interfaces** — each of the four segregated interfaces got a one-line class-level KDoc and
per-method KDoc with `@param` and `@return`:

- `JiraLabelChecker.isAutonomous` — returns true when the ticket carries the `autonomous` label
- `JiraTicketFetcher.getTicketContent` — returns formatted summary + ADF description, or null on failure
- `JiraCommentPoster.addComment` — posts plain text as an ADF paragraph; failures are swallowed
- `JiraTicketStatusChecker.getTicketStatus` — returns the Jira workflow status name, or null on failure

**`JiraApiService` class** — added a multi-line KDoc describing its role as the Jira Cloud REST
API v3 client, its Basic auth strategy, and the fail-safe design (log + return null/false instead
of throwing).

**Override methods** — each of the four overrides received KDoc matching the interface contract,
plus implementation-specific notes (e.g. ADF extraction for `getTicketContent`, payload size
minimisation for `isAutonomous`).

## Quality gates

- `./gradlew :agent:detekt` — passes
- `./scripts/run-affected-tests.sh` — no test class mapping for pure KDoc changes; CI is authoritative
