package com.mediasage.pipeline.core

import org.jetbrains.exposed.sql.Table

object TranscriptsTable : Table("transcripts") {
    val jobId = uuid("job_id").references(JobsTable.jobId)
    val content = text("content")
    override val primaryKey = PrimaryKey(jobId)
}
