package ru.pravbeseda.sleepnoise.review

/** Long enough to have been slept through, rather than a noise tried and switched off. */
const val LONG_SESSION_MILLIS = 30 * 60_000L

const val LONG_SESSIONS_BEFORE_REVIEW = 10

/** Local time, from 9:00 up to 20:00: nobody is asked for a rating while settling down to sleep. */
const val FIRST_REVIEW_HOUR = 9
const val END_REVIEW_HOUR = 20

fun isLongSession(durationMillis: Long): Boolean = durationMillis >= LONG_SESSION_MILLIS

/**
 * Whether the screen asks Google Play for its rating dialog now. Once only: the API never says whether the
 * dialog was shown or answered, so a request that went unanswered counts as an answer.
 */
fun isReviewDue(longSessions: Int, alreadyRequested: Boolean, hourOfDay: Int): Boolean =
    !alreadyRequested && longSessions >= LONG_SESSIONS_BEFORE_REVIEW && hourOfDay in FIRST_REVIEW_HOUR until END_REVIEW_HOUR
