package dev.todor.fassistant

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * The backstop behind [TickAlarm]. Persisted across reboots and owned by the system rather than by
 * us, so it survives things that clear alarms. Fifteen minutes is the platform floor for a periodic
 * job, which is why it cannot be the primary trigger.
 */
class TickJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        WatchdogService.start(this, "job")
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 41

        fun ensure(ctx: Context) {
            val scheduler = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, TickJob::class.java))
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            runCatching { scheduler.schedule(job) }
        }

        fun cancel(ctx: Context) {
            val scheduler = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }

        private const val PERIOD_MS = 15 * 60 * 1000L
    }
}
