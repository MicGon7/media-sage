package com.mediasage.pipeline.core

/**
 * The single in-code fallback model for pipeline services (the AgentRuntime support judge and the
 * advisor MCP tools) when their model env var is unset.
 *
 * Cloud Run deploys always set the model explicitly via the deploy workflows, so this constant is
 * the local-dev / safety fallback. It lives in `:pipelineCore` — the one module both `:agentruntime`
 * and `:advisor` depend on — so every service resolves to the *same* model when the env var is
 * absent, with no per-module drift.
 *
 * The coding-agent worker has its own fallback in `worker-entrypoint.sh` (a bash literal, since a
 * shell script cannot read this constant); keep the two in sync when bumping the model.
 */
const val DEFAULT_CLAUDE_MODEL: String = "claude-sonnet-5"
