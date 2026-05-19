package com.mediasage.agent.service

import com.mediasage.agent.db.JobRegistry

data class CloudRunDispatch(val dispatcher: JobDispatcher, val jobs: JobRegistry)
