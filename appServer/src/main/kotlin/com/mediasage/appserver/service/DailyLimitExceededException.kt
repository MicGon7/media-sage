package com.mediasage.appserver.service

class DailyLimitExceededException(
    override val message: String = "Daily Claude API call limit reached. Try again tomorrow."
) : RuntimeException(message)
