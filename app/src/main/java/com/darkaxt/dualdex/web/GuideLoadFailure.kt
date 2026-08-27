package com.darkaxt.dualdex.web

class GuideLoadFailure private constructor(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause) {
    companion object {
        fun from(cause: Throwable): GuideLoadFailure = GuideLoadFailure(
            message = if (cause is OutOfMemoryError) {
                "There was not enough free memory to open this game guide. Close other apps and try again."
            } else {
                "This game guide could not be opened. You can try again."
            },
            cause = cause,
        )
    }
}
