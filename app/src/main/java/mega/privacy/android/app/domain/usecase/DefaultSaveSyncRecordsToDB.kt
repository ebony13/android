package mega.privacy.android.app.domain.usecase

import kotlinx.coroutines.yield
import mega.privacy.android.app.utils.Util
import mega.privacy.android.app.utils.wrapper.TimeWrapper
import mega.privacy.android.domain.entity.SyncRecord
import mega.privacy.android.domain.usecase.DeleteSyncRecordByLocalPath
import mega.privacy.android.domain.usecase.FileNameExists
import mega.privacy.android.domain.usecase.GetSyncRecordByFingerprint
import mega.privacy.android.domain.usecase.KeepFileNames
import mega.privacy.android.domain.usecase.SaveSyncRecord
import nz.mega.sdk.MegaNode
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Helper use case to save sync records to database
 * Can be replaced by a worker after the camera upload service is removed
 *
 */
class DefaultSaveSyncRecordsToDB @Inject constructor(
    private val getSyncRecordByFingerprint: GetSyncRecordByFingerprint,
    private val deleteSyncRecordByLocalPath: DeleteSyncRecordByLocalPath,
    private val keepFileNames: KeepFileNames,
    private val getChildMegaNode: GetChildMegaNode,
    private val fileNameExists: FileNameExists,
    private val saveSyncRecord: SaveSyncRecord,
    private val timeWrapper: TimeWrapper,
) : SaveSyncRecordsToDB {

    override suspend fun invoke(
        list: List<SyncRecord>,
        primaryUploadNode: MegaNode?,
        secondaryUploadNode: MegaNode?,
        rootPath: String?,
    ) {
        for (file in list) {
            run {
                Timber.d("Handle with local file which timestamp is: %s", file.timestamp)
                yield()

                val exist = getSyncRecordByFingerprint(file.originFingerprint,
                    file.isSecondary,
                    file.isCopyOnly)
                if (exist != null) {
                    exist.timestamp?.let { existTime ->
                        file.timestamp?.let { fileTime ->
                            if (existTime < fileTime) {
                                Timber.d("Got newer time stamp.")
                                exist.localPath?.let {
                                    deleteSyncRecordByLocalPath(it, exist.isSecondary)
                                }
                            } else {
                                Timber.w("Duplicate sync records.")
                                return@run
                            }
                        }
                    }
                }

                val isSecondary = file.isSecondary
                val parent = if (isSecondary) secondaryUploadNode else primaryUploadNode
                if (!file.isCopyOnly) {
                    val resFile = file.localPath?.let { File(it) }
                    if (resFile != null && !resFile.exists()) {
                        Timber.w("File does not exist, remove from database.")
                        file.localPath?.let {
                            deleteSyncRecordByLocalPath(it, isSecondary)
                        }
                        return@run
                    }
                }

                var fileName: String?
                var inCloud: Boolean
                var inDatabase = false
                var photoIndex = 0
                if (keepFileNames()) {
                    //Keep the file names as device but need to handle same file name in different location
                    val tempFileName = file.fileName
                    do {
                        yield()
                        fileName = getNoneDuplicatedDeviceFileName(tempFileName, photoIndex)
                        Timber.d("Keep file name as in device, name index is: %s", photoIndex)
                        photoIndex++
                        inCloud = getChildMegaNode(parent, fileName) != null
                        fileName?.let {
                            inDatabase = fileNameExists(it, isSecondary)
                        }
                    } while (inCloud || inDatabase)
                } else {
                    do {
                        yield()
                        fileName = Util.getPhotoSyncNameWithIndex(getLastModifiedTime(file),
                            file.localPath,
                            photoIndex)
                        Timber.d("Use MEGA name, name index is: %s", photoIndex)
                        photoIndex++
                        inCloud = getChildMegaNode(parent, fileName) != null
                        inDatabase = fileNameExists(fileName, isSecondary)
                    } while (inCloud || inDatabase)
                }

                var extension = ""
                fileName?.let {
                    val splitName = it.split("\\.").toTypedArray()
                    if (splitName.isNotEmpty()) {
                        extension = splitName[splitName.size - 1]
                    }
                }
                file.fileName = fileName
                val newPath = "$rootPath${timeWrapper.nanoTime}.$extension"
                file.newPath = newPath
                Timber.d("Save file to database, new path is: %s", newPath)
                saveSyncRecord(file)
            }
        }
    }

    private fun getLastModifiedTime(file: SyncRecord): Long {
        val source = file.localPath?.let { File(it) }
        return source?.lastModified() ?: 0
    }

    private fun getNoneDuplicatedDeviceFileName(fileName: String?, index: Int): String? {
        if (index == 0) {
            return fileName
        }
        var name = ""
        var extension = ""
        val pos = fileName?.lastIndexOf(".")
        if (pos != null && pos > 0) {
            name = fileName.substring(0, pos)
            extension = fileName.substring(pos)
        }
        return "${name}_$index$extension"
    }
}
