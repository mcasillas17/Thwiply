package thwiply.elopenmike.com

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.data.cleanup.NotificationMaintenanceScheduler
import thwiply.elopenmike.com.di.ApplicationScope
import thwiply.elopenmike.com.domain.cleanup.NotificationCleanupTrigger
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator

@HiltAndroidApp
class ThwiplyApp : Application() {
    @Inject
    lateinit var cleanupCoordinator: NotificationDataCleanupCoordinator

    @Inject
    lateinit var maintenanceScheduler: NotificationMaintenanceScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Startup cleanup and scheduler registration both stay off the main thread.
        applicationScope.launch {
            maintenanceScheduler.ensureScheduled()
            cleanupCoordinator.cleanUpExpiredNotificationData(
                NotificationCleanupTrigger.APP_STARTUP,
            )
        }
    }
}
