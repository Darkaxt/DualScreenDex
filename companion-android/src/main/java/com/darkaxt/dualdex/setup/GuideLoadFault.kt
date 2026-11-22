package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import java.util.concurrent.atomic.AtomicReference

fun interface GuideLoadFault {
    /** Returns a deterministic fault before source loading, or null for normal production behavior. */
    fun beforeLoad(entry: RomIndexEntry): Throwable?
}

object NoGuideLoadFault : GuideLoadFault {
    override fun beforeLoad(entry: RomIndexEntry): Throwable? = null
}

class OneShotGuideLoadFault : GuideLoadFault {
    private val nextFailure = AtomicReference<Throwable?>()

    fun failNext(failure: Throwable) {
        nextFailure.set(failure)
    }

    override fun beforeLoad(entry: RomIndexEntry): Throwable? = nextFailure.getAndSet(null)
}
