package mega.privacy.android.domain.usecase

import mega.privacy.android.domain.repository.CameraUploadRepository
import javax.inject.Inject

/**
 * Default Implementation of DisableCameraUploadSettings
 *
 */
class DefaultDisableMediaUploadSettings @Inject constructor(
    private val cameraUploadRepository: CameraUploadRepository,
) : DisableMediaUploadSettings {

    override suspend fun invoke() {
        cameraUploadRepository.setSecondaryEnabled(false)
    }
}