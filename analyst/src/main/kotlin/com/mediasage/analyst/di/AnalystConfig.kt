package com.mediasage.analyst.di

/**
 * Configuration for the Analyst feedback server.
 *
 * The Analyst is a reactive consumer of pipeline completion events. It needs only two
 * runtime parameters: a read connection to the existing Supabase `jobs` table, and the
 * shared secret used to authenticate Pub/Sub push deliveries.
 *
 * All values are sourced from environment variables via `application.conf` using Ktor's
 * `${?VAR}` substitution syntax.
 *
 * @property supabaseDbUrl PostgreSQL connection URL for the Supabase job registry. The Analyst
 *   reads cross-run history from the `jobs` table written by the orchestrator; it never writes
 *   to it. Sourced from env var `SUPABASE_DB_URL`.
 * @property pubSubWebhookSecret Shared secret token appended as `?token=` to the Analyst's Pub/Sub
 *   push subscription URL. Verified on every push delivery to reject spoofed requests. Sourced from
 *   env var `PUBSUB_WEBHOOK_SECRET`. When blank, the Pub/Sub route is not registered.
 */
data class AnalystConfig(
    val supabaseDbUrl: String = "",
    val pubSubWebhookSecret: String = "",
)
