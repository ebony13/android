package mega.privacy.android.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mega.privacy.android.data.R
import mega.privacy.android.data.gateway.AppInfoGateway
import mega.privacy.android.data.gateway.DeviceGateway
import mega.privacy.android.data.gateway.api.MegaApiGateway
import mega.privacy.android.data.gateway.preferences.AppInfoPreferencesGateway
import mega.privacy.android.domain.entity.AppInfo
import mega.privacy.android.domain.entity.DeviceInfo
import mega.privacy.android.domain.qualifier.IoDispatcher
import mega.privacy.android.domain.repository.EnvironmentRepository
import javax.inject.Inject

/**
 * [EnvironmentRepository] Implementation
 *
 */
internal class EnvironmentRepositoryImpl @Inject constructor(
    private val deviceGateway: DeviceGateway,
    @ApplicationContext private val context: Context,
    private val megaApiGateway: MegaApiGateway,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val appInfoGateway: AppInfoGateway,
    private val appInfoPreferencesGateway: AppInfoPreferencesGateway,
) : EnvironmentRepository {

    override suspend fun getDeviceInfo() =
        withContext(ioDispatcher) { DeviceInfo(getDeviceName(), getLanguage()) }

    private fun getDeviceName(): String {
        val manufacturer = deviceGateway.getManufacturerName().run {
            if (this.equals("HTC", true)) uppercase() else this
        }
        val model = deviceGateway.getDeviceModel()
        return "$manufacturer $model"
    }

    private fun getLanguage(): String = deviceGateway.getCurrentDeviceLanguage()

    override suspend fun getAppInfo(): AppInfo = withContext(ioDispatcher) {
        AppInfo(
            appVersion = context.getString(R.string.app_version),
            sdkVersion = megaApiGateway.getSdkVersion(),
        )
    }

    override suspend fun getLastSavedVersionCode() =
        withContext(ioDispatcher) { appInfoPreferencesGateway.monitorLastVersionCode().first() }

    override suspend fun getInstalledVersionCode() = withContext(ioDispatcher) {
        appInfoGateway.getAppVersionCode()
    }

    override suspend fun saveVersionCode(newVersionCode: Int) {
        withContext(ioDispatcher) { appInfoPreferencesGateway.setLastVersionCode(newVersionCode) }
    }

    override suspend fun getDeviceSdkVersionInt() =
        withContext(ioDispatcher) { deviceGateway.getSdkVersionInt() }

    override suspend fun getDeviceSdkVersionName() =
        withContext(ioDispatcher) { deviceGateway.getSdkVersionName() }

    override suspend fun getDeviceMemorySizeInBytes() = withContext(ioDispatcher) {
        deviceGateway.getDeviceMemory()
    }
}
