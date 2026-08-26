package com.mediasage.appserver.repository

import com.mediasage.appserver.db.ClaudeCallLimitTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ClaudeCallLimitRepository {

    /** Atomically checks and increments today's Claude call count. Returns false once [dailyLimit] is already reached. */
    suspend fun tryConsumeCall(callDate: String, dailyLimit: Int): Boolean = withContext(Dispatchers.IO) {
        transaction {
            val existing = ClaudeCallLimitTable.selectAll()
                .where { ClaudeCallLimitTable.callDate eq callDate }
                .singleOrNull()

            if (existing == null) {
                ClaudeCallLimitTable.insert {
                    it[ClaudeCallLimitTable.callDate] = callDate
                    it[callCount] = 1
                }
                true
            } else {
                val count = existing[ClaudeCallLimitTable.callCount]
                if (count >= dailyLimit) {
                    false
                } else {
                    ClaudeCallLimitTable.update({ ClaudeCallLimitTable.callDate eq callDate }) {
                        it[callCount] = count + 1
                    }
                    true
                }
            }
        }
    }
}
