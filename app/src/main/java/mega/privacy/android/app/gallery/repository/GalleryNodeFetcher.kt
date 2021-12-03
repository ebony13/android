package mega.privacy.android.app.gallery.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay
import mega.privacy.android.app.fragments.homepage.TypedNodesFetcher
import mega.privacy.android.app.gallery.data.GalleryItem
import mega.privacy.android.app.gallery.data.GalleryItem.Companion.TYPE_HEADER
import mega.privacy.android.app.gallery.data.GalleryItem.Companion.TYPE_IMAGE
import mega.privacy.android.app.listeners.OptionalMegaRequestListenerInterface
import mega.privacy.android.app.utils.*
import mega.privacy.android.app.utils.Constants.INVALID_POSITION
import mega.privacy.android.app.utils.StringUtils.formatDateTitle
import nz.mega.sdk.*
import nz.mega.sdk.MegaApiJava.*
import java.io.File
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter.ofPattern
import java.util.*

/**
 * Data fetcher for fetching typed files
 */
class GalleryNodeFetcher(
    private val context: Context,
    private val megaApi: MegaApiAndroid,
    private val selectedNodesMap: LinkedHashMap<Any, GalleryItem>,
    private val zoom: Int
) : TypedNodesFetcher<GalleryItem>(context, megaApi, selectedNodesMap = selectedNodesMap) {

    private val getPreviewNodes = mutableMapOf<MegaNode, String>()

    /**
     * Throttle for updating the LiveData
     */
    private fun refreshLiveData() {
        if (waitingForRefresh) return
        waitingForRefresh = true

        Handler(Looper.getMainLooper()).postDelayed(
            {
                waitingForRefresh = false
                result.postValue(ArrayList(fileNodesMap.values))
            }, UPDATE_DATA_THROTTLE_TIME
        )
    }

    private fun getPreviewFile(node: MegaNode) = File(
        PreviewUtils.getPreviewFolder(context),
        node.base64Handle.plus(FileUtil.JPG_EXTENSION)
    )

    /**
     * Get the thumbnail of the file.
     */
    private fun getThumbnail(node: MegaNode): File? {
        val thumbFile = getThumbnailFile(node)

        return if (thumbFile.exists()) {
            thumbFile
        } else {
            // Note down the nodes and going to get their thumbnails from the server
            // as soon as the getGalleryItems finished. (Don't start the getting operation here
            // for avoiding potential ConcurrentModification issue)
            if (node.hasThumbnail()) {
                getThumbnailNodes[node] = thumbFile.absolutePath
            }

            null
        }
    }

    /**
     * Get the preview of the file.
     */
    private fun getPreview(node: MegaNode): File? {
        val previewFile = getPreviewFile(node)

        return if (previewFile.exists()) {
            previewFile
        } else {
            // Note down the nodes and going to get their previews from the server
            // as soon as the getGalleryItems finished. (Don't start the getting operation here
            // for avoiding potential ConcurrentModification issue)
            if (node.hasPreview()) {
                getPreviewNodes[node] = previewFile.absolutePath
            }

            null
        }
    }

    /**
     * Get all nodes items
     */
    suspend fun getGalleryItems() {
        var lastYearDate: LocalDate? = null
        var lastMonthDate: LocalDate? = null
        var lastDayDate: LocalDate? = null

        for (node in getMegaNodes(ORDER_MODIFICATION_DESC, FILE_TYPE_PHOTO)) {
            val thumbnail = if (zoom == ZoomUtil.ZOOM_IN_1X) {
                getPreview(node)
            } else {
                getThumbnail(node)
            }

            val modifyDate = Util.fromEpoch(node.modificationTime)
            val dateString = ofPattern("MMMM uuuu").format(modifyDate)
            val sameYear = Year.from(LocalDate.now()) == Year.from(modifyDate)

            // Photo "Month-Year" section headers
            when (zoom) {
                ZoomUtil.ZOOM_OUT_2X -> {
                    if (lastYearDate == null || Year.from(lastYearDate) != Year.from(modifyDate)) {
                        lastYearDate = modifyDate
                        addPhotoDateTitle(
                            dateString,
                            Pair(ofPattern("uuuu").format(modifyDate), "")
                        )
                    }
                }
                ZoomUtil.ZOOM_IN_1X -> {
                    if (lastDayDate == null || lastDayDate.dayOfYear != modifyDate.dayOfYear) {
                        lastDayDate = modifyDate

                        addPhotoDateTitle(
                            dateString, Pair(
                                ofPattern("dd MMMM").format(modifyDate),
                                if (sameYear) "" else ofPattern("uuuu").format(modifyDate)
                            )
                        )
                    }
                }
                else -> {
                    if (lastMonthDate == null || YearMonth.from(lastMonthDate) != YearMonth.from(
                            modifyDate
                        )
                    ) {
                        lastMonthDate = modifyDate
                        addPhotoDateTitle(
                            dateString, Pair(
                                ofPattern("MMMM").format(modifyDate),
                                if (sameYear) "" else ofPattern("uuuu").format(modifyDate)
                            )
                        )
                    }
                }
            }


            val selected = selectedNodesMap[node.handle]?.selected ?: false
            fileNodesMap[node.handle] = GalleryItem(
                node,
                INVALID_POSITION,
                INVALID_POSITION,
                thumbnail,
                TYPE_IMAGE,
                dateString,
                null,
                null,
                selected,
                true
            )
        }

        result.postValue(ArrayList(fileNodesMap.values))

        getThumbnailsFromServer()

        if (zoom == ZoomUtil.ZOOM_IN_1X) {
            getPreviewsFromServer(getPreviewNodes, ::refreshLiveData)
        }
    }

    private fun addPhotoDateTitle(dateString: String, date: Pair<String, String>) {
        // RandomUUID() can ensure non-repetitive values in practical purpose
        fileNodesMap[UUID.randomUUID()] = GalleryItem(
            null,
            INVALID_POSITION,
            INVALID_POSITION,
            null,
            TYPE_HEADER,
            dateString,
            date.formatDateTitle(),
            null,
            false,
            uiDirty = true
        )
    }

    suspend fun getPreviewsFromServer(
        map: MutableMap<MegaNode, String>,
        refreshCallback: () -> Unit
    ) {
        for (item in map) {
            megaApi.getPreview(
                item.key,
                item.value,
                OptionalMegaRequestListenerInterface(
                    onRequestFinish = { request, error ->
                        if (error.errorCode != MegaError.API_OK) return@OptionalMegaRequestListenerInterface

                        request.let {
                            fileNodesMap[it.nodeHandle]?.apply {
                                thumbnail = getPreviewFile(item.key).absoluteFile
                                uiDirty = true
                            }
                        }

                        refreshCallback.invoke()
                    }
                ))

            // Throttle the getThumbnail call, or the UI would be non-responsive
            delay(GET_THUMBNAIL_THROTTLE)
        }
    }
}