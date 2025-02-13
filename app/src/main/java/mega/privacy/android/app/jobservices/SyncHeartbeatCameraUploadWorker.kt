package mega.privacy.android.app.jobservices

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mega.privacy.android.app.sync.camerauploads.CameraUploadSyncManagerWrapper
import timber.log.Timber

/**
 * Worker to send regular camera upload sync heart beats
 */
@HiltWorker
class SyncHeartbeatCameraUploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cameraUploadSyncManagerWrapper: CameraUploadSyncManagerWrapper
) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        if (isStopped) return Result.failure()
        Timber.d("SyncHeartbeatCameraUploadWorker: doWork()")
        return try {
            cameraUploadSyncManagerWrapper.doRegularHeartbeat()
            Result.success()
        } catch (throwable: Throwable) {
            Timber.d("SyncHeartbeatCameraUploadWorker: doWork() fail")
            Result.failure()
        }
    }
}
