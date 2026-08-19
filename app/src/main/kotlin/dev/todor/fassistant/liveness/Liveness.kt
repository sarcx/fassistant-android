package dev.todor.fassistant.liveness

import dev.todor.fassistant.WatchedApp

enum class Liveness { ALIVE, DEAD, UNKNOWN }

class Verdict(val liveness: Liveness, val source: String)

interface LivenessSignal {
    val id: String

    fun available(): Boolean

    fun refresh(now: Long) {}

    fun check(app: WatchedApp, now: Long): Liveness
}
