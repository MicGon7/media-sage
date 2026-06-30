package com.mediasage.agentruntime

internal object AnthropicApi {
    const val VERSION = "2023-06-01"

    object TokenBudget {
        const val COMPACT = 2048   // classification, structured scoring
        const val STANDARD = 4096  // synthesis, patch generation
    }
}
