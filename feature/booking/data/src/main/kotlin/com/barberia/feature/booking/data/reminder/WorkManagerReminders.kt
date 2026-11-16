package com.barberia.feature.booking.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.barberia.core.common.AppResult
import com.barberia.feature.booking.data.R
import com.barberia.feature.booking.domain.model.shouldNotify
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.booking.domain.repository.ReminderScheduler
import com.barberia.feature.catalog.domain.model.TimeRange
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Programa un trabajo único por cita: volver a programar la misma cita lo reemplaza (R3, sin duplicados). */
internal class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {

    override fun schedule(tenantId: String, appointmentId: String, at: Instant) {
        val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TENANT to tenantId, KEY_APPOINTMENT to appointmentId))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(appointmentId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(appointmentId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(name(appointmentId))
    }

    companion object {
        const val TAG = "appointment_reminder"
        const val KEY_TENANT = "tenantId"
        const val KEY_APPOINTMENT = "appointmentId"
        fun name(appointmentId: String) = "reminder_$appointmentId"
    }
}

/**
 * R2: al momento de avisar se vuelve a leer la cita (1 lectura; sin red usa la caché). Si ya no está
 * vigente (la canceló el personal o una penalización), no se avisa.
 */
internal class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val bookings: BookingRepository by inject()

    override suspend fun doWork(): Result {
        val tenantId = inputData.getString(WorkManagerReminderScheduler.KEY_TENANT) ?: return Result.success()
        val id = inputData.getString(WorkManagerReminderScheduler.KEY_APPOINTMENT) ?: return Result.success()
        val appointment = when (val r = bookings.appointment(tenantId, id)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> return Result.retry()
        }
        if (appointment == null || !shouldNotify(appointment, Instant.now())) return Result.success() // R2, R6
        notify(
            id,
            applicationContext.getString(R.string.reminder_title, TimeRange.label(appointment.startMinute)),
            applicationContext.getString(R.string.reminder_text, appointment.serviceName, appointment.barberName),
        )
        return Result.success()
    }

    private fun notify(id: String, title: String, text: String) {
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled()) return // R4: sin permiso no se avisa (y no truena)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, applicationContext.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let {
            PendingIntent.getActivity(applicationContext, id.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // comprobado arriba con areNotificationsEnabled()
        nm.notify(id.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL = "appointment_reminders"
    }
}
