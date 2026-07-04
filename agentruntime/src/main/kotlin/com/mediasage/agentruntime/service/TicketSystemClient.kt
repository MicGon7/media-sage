package com.mediasage.agentruntime.service

/**
 * Abstracts ticket-system operations needed for dispatch-on-unblock sequencing.
 *
 * Implemented by [JiraTicketClient], which contains all Jira-specific REST logic.
 * The dispatch handler references only this interface so the underlying ticket system
 * can be swapped without touching dispatch logic.
 */
interface TicketSystemClient {

    /**
     * Returns keys of tickets that are newly unblocked as a result of [mergedTicketKey] being resolved.
     *
     * A ticket qualifies when:
     * - it was blocked by [mergedTicketKey]
     * - all of its remaining blockers are now resolved
     * - it is assigned to the bot account
     *
     * @param mergedTicketKey Ticket key whose PR just merged (e.g. "MS-520").
     * @return Keys of fully unblocked, bot-assigned tickets, or empty list if none.
     */
    suspend fun getNewlyUnblockedTickets(mergedTicketKey: String): List<String>

    /**
     * Returns true if [ticketKey] is in a resolved state (e.g. "Done" in Jira).
     *
     * @param ticketKey Ticket key to inspect (e.g. "MS-520").
     */
    suspend fun isResolved(ticketKey: String): Boolean
}
