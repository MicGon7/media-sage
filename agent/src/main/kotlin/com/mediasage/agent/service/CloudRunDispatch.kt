package com.mediasage.agent.service

import com.mediasage.agent.db.JobRepository

data class CloudRunDispatch(val dispatcher: JobDispatcher, val jobs: JobRepository)
