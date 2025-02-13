package mega.privacy.android.app.presentation.clouddrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.domain.usecase.GetBrowserChildrenNode
import mega.privacy.android.app.domain.usecase.GetRootFolder
import mega.privacy.android.app.domain.usecase.MonitorNodeUpdates
import mega.privacy.android.app.presentation.clouddrive.model.FileBrowserState
import mega.privacy.android.app.presentation.settings.model.MediaDiscoveryViewSettings
import mega.privacy.android.domain.usecase.GetParentNodeHandle
import mega.privacy.android.domain.usecase.MonitorMediaDiscoveryView
import nz.mega.sdk.MegaApiJava
import nz.mega.sdk.MegaNode
import java.util.Stack
import javax.inject.Inject

/**
 * ViewModel associated to FileBrowserFragment
 *
 * @param getRootFolder Fetch the root node
 * @param getBrowserChildrenNode Fetch the cloud drive nodes
 * @param monitorMediaDiscoveryView Monitor media discovery view settings
 * @param monitorNodeUpdates Monitor node updates
 * @param getFileBrowserParentNodeHandle To get parent handle of current node
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val getRootFolder: GetRootFolder,
    private val getBrowserChildrenNode: GetBrowserChildrenNode,
    private val monitorMediaDiscoveryView: MonitorMediaDiscoveryView,
    private val monitorNodeUpdates: MonitorNodeUpdates,
    private val getFileBrowserParentNodeHandle: GetParentNodeHandle
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())

    /**
     * State flow
     */
    val state: StateFlow<FileBrowserState> = _state

    /**
     * Stack to maintain folder navigation clicks
     */
    private val lastPositionStack = Stack<Int>()

    init {
        monitorMediaDiscovery()
        monitorFileBrowserChildrenNodes()
    }

    /**
     * This will monitor media discovery from [MonitorMediaDiscoveryView] and update
     * [FileBrowserState.mediaDiscoveryViewSettings]
     */
    private fun monitorMediaDiscovery() {
        viewModelScope.launch {
            monitorMediaDiscoveryView().collect { mediaDiscoveryViewSettings ->
                _state.update {
                    it.copy(
                        mediaDiscoveryViewSettings = mediaDiscoveryViewSettings
                            ?: MediaDiscoveryViewSettings.INITIAL.ordinal
                    )
                }
            }
        }
    }

    /**
     * This will monitor FileBrowserNodeUpdates from [MonitorNodeUpdates] and
     * will update [FileBrowserState.nodes]
     */
    private fun monitorFileBrowserChildrenNodes() {
        viewModelScope.launch {
            refreshNodes()
            monitorNodeUpdates().collect {
                refreshNodes()
            }
        }
    }

    /**
     * Set the current browser handle to the UI state
     *
     * @param handle the id of the current browser handle to set
     */
    fun setBrowserParentHandle(handle: Long) = viewModelScope.launch {
        _state.update {
            it.copy(
                fileBrowserHandle = handle,
                mediaHandle = handle
            )
        }
        refreshNodes()
    }

    /**
     * Get the browser parent handle
     * If not previously set, set the browser parent handle to root handle
     *
     * @return the handle of the browser section
     */
    fun getSafeBrowserParentHandle(): Long = runBlocking {
        if (_state.value.fileBrowserHandle == -1L) {
            setBrowserParentHandle(getRootFolder()?.handle ?: MegaApiJava.INVALID_HANDLE)
        }
        return@runBlocking _state.value.fileBrowserHandle
    }

    /**
     * If a folder only contains images or videos, then go to MD mode directly
     */
    fun shouldEnterMDMode(mediaDiscoveryViewSettings: Int): Boolean {
        if (_state.value.nodes.isEmpty())
            return false
        val isMediaDiscoveryEnable =
            mediaDiscoveryViewSettings == MediaDiscoveryViewSettings.ENABLED.ordinal ||
                    mediaDiscoveryViewSettings == MediaDiscoveryViewSettings.INITIAL.ordinal
        if (!isMediaDiscoveryEnable)
            return false

        for (node: MegaNode in _state.value.nodes) {
            if (node.isFolder ||
                !MimeTypeList.typeForName(node.name).isImage &&
                !MimeTypeList.typeForName(node.name).isVideoReproducible
            ) {
                return false
            }
        }
        return true
    }

    /**
     * This will refresh filebrowser nodes and update [FileBrowserState.nodes]
     */
    fun refreshNodes() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    nodes = getBrowserChildrenNode(_state.value.fileBrowserHandle) ?: emptyList(),
                    parentHandle = getFileBrowserParentNodeHandle(_state.value.fileBrowserHandle)
                )
            }
        }
    }

    /**
     * Handles back click of rubbishBinFragment
     */
    fun onBackPressed() {
        _state.value.parentHandle?.let {
            setBrowserParentHandle(it)
        }
    }

    /**
     * Pop scroll position for previous depth
     *
     * @return last position saved
     */
    fun popLastPositionStack(): Int = lastPositionStack.takeIf { it.isNotEmpty() }?.pop() ?: 0

    /**
     * Push lastPosition to stack
     * @param lastPosition last position to be added to stack
     */
    private fun pushPositionOnStack(lastPosition: Int) {
        lastPositionStack.push(lastPosition)
    }

    /**
     * Performs action when folder is clicked from adapter
     * @param lastFirstVisiblePosition visible position based on listview type
     * @param handle node handle
     */
    fun onFolderItemClicked(lastFirstVisiblePosition: Int, handle: Long) {
        pushPositionOnStack(lastFirstVisiblePosition)
        setBrowserParentHandle(handle)
    }
}
