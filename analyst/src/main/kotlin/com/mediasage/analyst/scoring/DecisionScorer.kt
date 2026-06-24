package com.mediasage.analyst.scoring

import java.util.UUID

/**
 * Scores a completed worker session against the decision-scoring rubric.
 *
 * Implementations read the session transcript from Supabase, call Claude as a judge, and persist
 * the resulting scores to the `decision_scores` table. The Pub/Sub completion handler fires this
 * after returning 200 so scoring is never in the critical delivery path.
 */
interface DecisionScorer {
    /**
     * Score the transcript for [jobId] against all rubric criteria and persist the results.
     *
     * Returns silently if no transcript exists for the job (e.g. a pre-MS-387 run). Does not
     * throw — caller fire-and-forgets this from the Pub/Sub handler.
     */
    suspend fun score(jobId: UUID)
}
