package mega.privacy.android.app.namecollision

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.facebook.imagepipeline.request.ImageRequest
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.activities.PasscodeActivity
import mega.privacy.android.app.activities.WebViewActivity
import mega.privacy.android.app.constants.BroadcastConstants.ACTION_UPDATE_FILE_VERSIONS
import mega.privacy.android.app.databinding.ActivityNameCollisionBinding
import mega.privacy.android.app.fragments.homepage.EventObserver
import mega.privacy.android.app.namecollision.data.NameCollision
import mega.privacy.android.app.namecollision.data.NameCollisionResult
import mega.privacy.android.app.utils.AlertsAndWarnings.showForeignStorageOverQuotaWarningDialog
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.StringResourcesUtils
import mega.privacy.android.app.utils.StringUtils.formatColorTag
import mega.privacy.android.app.utils.StringUtils.toSpannedHtmlText
import mega.privacy.android.app.utils.TimeUtils.formatLongDateTime
import mega.privacy.android.app.utils.Util
import mega.privacy.android.app.utils.Util.getSizeString

/**
 * Activity for showing name collisions and resolving them as per user's choices.
 */
class NameCollisionActivity : PasscodeActivity() {

    companion object {

        private const val LEARN_MORE_URI =
            "https://help.mega.io/files-folders/restore-delete/file-version-history"

        private const val UPLOAD_FOLDER_CONTEXT = "UPLOAD_FOLDER_CONTEXT"

        @JvmStatic
        fun getIntentForList(
            context: Context,
            collisions: ArrayList<NameCollision>
        ): Intent =
            Intent(context, NameCollisionActivity::class.java).apply {
                putExtra(INTENT_EXTRA_COLLISION_RESULTS, collisions)
            }

        @JvmStatic
        fun getIntentForFolderUpload(
            context: Context,
            collisions: ArrayList<NameCollision>
        ): Intent =
            getIntentForList(context, collisions).apply { action = UPLOAD_FOLDER_CONTEXT }

        @JvmStatic
        fun getIntentForSingleItem(
            context: Context,
            collision: NameCollision
        ): Intent =
            Intent(context, NameCollisionActivity::class.java).apply {
                putExtra(INTENT_EXTRA_SINGLE_COLLISION_RESULT, collision)
            }
    }

    private val viewModel: NameCollisionViewModel by viewModels()

    private lateinit var binding: ActivityNameCollisionBinding

    private val elevation by lazy { resources.getDimension(R.dimen.toolbar_elevation) }
    private val elevationColor by lazy {
        ContextCompat.getColor(this, R.color.action_mode_background)
    }
    private val noElevationColor by lazy { ContextCompat.getColor(this, R.color.dark_grey) }

    private val updateFileVersionsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_UPDATE_FILE_VERSIONS != intent.action) return

            viewModel.updateFileVersioningInfo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNameCollisionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            @Suppress("UNCHECKED_CAST")
            val collisionsList =
                intent.getSerializableExtra(INTENT_EXTRA_COLLISION_RESULTS) as ArrayList<NameCollision>?
            val singleCollision =
                intent.getSerializableExtra(INTENT_EXTRA_SINGLE_COLLISION_RESULT) as NameCollision?

            when {
                collisionsList != null -> viewModel.setData(collisionsList)
                singleCollision != null -> viewModel.setSingleData(singleCollision)
                else -> {
                    logError("No collisions received")
                    finish()
                }
            }

            viewModel.isFolderUploadContext = UPLOAD_FOLDER_CONTEXT == intent.action
        }

        setupView()
        setupObservers()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateFileVersionsReceiver)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun setupView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = StringResourcesUtils.getString(R.string.title_duplicated_items).uppercase()
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.scrollView.setOnScrollChangeListener { v, _, _, _, _ ->
            val showElevation = v.canScrollVertically(RecyclerView.NO_POSITION)

            binding.toolbar.elevation = if (showElevation) elevation else 0F

            if (Util.isDarkMode(this@NameCollisionActivity)) {
                val color = if (showElevation) elevationColor else noElevationColor
                window.statusBarColor = color
                binding.toolbar.setBackgroundColor(color)
            }
        }

        binding.learnMore.setOnClickListener {
            startActivity(
                Intent(
                    this@NameCollisionActivity,
                    WebViewActivity::class.java
                ).apply { data = LEARN_MORE_URI.toUri() })
        }
        binding.replaceUpdateMergeButton.setOnClickListener {
            viewModel.replaceUpdateOrMerge(this, binding.applyForAllCheck.isChecked)
        }
        binding.cancelButton.setOnClickListener {
            viewModel.cancel(binding.applyForAllCheck.isChecked)
        }
        binding.renameButton.setOnClickListener {
            viewModel.rename(binding.applyForAllCheck.isChecked)
        }
    }

    private fun setupObservers() {
        viewModel.getCurrentCollision().observe(this, ::showCollision)
        viewModel.getFileVersioningInfo().observe(this, ::updateFileVersioningData)
        viewModel.onActionResult().observe(this, EventObserver { result ->
            if (result.isForeignNode) {
                showForeignStorageOverQuotaWarningDialog(this)
            }

            showSnackbar(binding.root, result.message)

            if (result.shouldFinish) {
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, LONG_SNACKBAR_DURATION)
            }
        })
        viewModel.getCollisionsResolution().observe(this, ::manageCollisionsResolution)

        registerReceiver(
            updateFileVersionsReceiver,
            IntentFilter(ACTION_UPDATE_FILE_VERSIONS)
        )
    }

    /**
     * Shows the current collision.
     *
     * @param collisionResult   Object containing all the required info to present a collision.
     */
    private fun showCollision(collisionResult: NameCollisionResult?) {
        if (collisionResult == null) {
            logError("Cannot show any collision. Finishing...")
            finish()
            return
        }

        val collision = collisionResult.nameCollision
        val isFile = collision.isFile
        val name = collision.name

        binding.alreadyExistsText.text = StringResourcesUtils.getString(
            if (isFile) R.string.file_already_exists_in_location
            else R.string.folder_already_exists_in_location, name
        ).formatColorTag(this, 'B', R.color.grey_900_grey_100)
            .toSpannedHtmlText()

        binding.selectText.text = StringResourcesUtils.getString(
            if (isFile) R.string.choose_file
            else R.string.choose_folder
        )

        binding.replaceUpdateMergeView.apply {
            val hasThumbnail = collisionResult.thumbnail != null
            thumbnail.isVisible = hasThumbnail
            thumbnailIcon.isVisible = !hasThumbnail
            if (hasThumbnail) {
                thumbnail.setImageRequest(ImageRequest.fromUri(collisionResult.thumbnail))
            } else {
                thumbnailIcon.setImageResource(
                    if (isFile) MimeTypeList.typeForName(name).iconResourceId
                    else R.drawable.ic_folder_list
                )
            }
            this.name.text = name
            size.text = getSizeString(collision.size)
            date.text = formatLongDateTime(collision.lastModified)

            val thumbnailView = if (hasThumbnail) R.id.thumbnail else R.id.thumbnail_icon

            ConstraintSet().apply {
                clone(root)
                connect(R.id.name, ConstraintSet.TOP, thumbnailView, ConstraintSet.TOP)
                applyTo(root)
            }
        }

        binding.cancelInfo.text = StringResourcesUtils.getString(
            if (isFile) R.string.skip_file
            else R.string.skip_folder
        )

        val cancelButtonId: Int
        val renameInfoId: Int
        val renameButtonId: Int

        when (collision) {
            is NameCollision.Upload -> {
                cancelButtonId = R.string.do_not_upload
                renameInfoId = R.string.warning_upload_and_rename
                renameButtonId = R.string.upload_and_rename
            }
            is NameCollision.Copy -> {
                cancelButtonId = R.string.do_not_copy
                renameInfoId = R.string.warning_copy_and_rename
                renameButtonId = R.string.copy_and_rename
            }
            is NameCollision.Movement -> {
                cancelButtonId = R.string.do_not_move
                renameInfoId = R.string.warning_move_and_rename
                renameButtonId = R.string.move_and_rename
            }
        }

        binding.cancelView.apply {
            val hasThumbnail = collisionResult.collisionThumbnail != null
            thumbnail.isVisible = hasThumbnail
            thumbnailIcon.isVisible = !hasThumbnail
            if (hasThumbnail) {
                thumbnail.setImageRequest(ImageRequest.fromUri(collisionResult.collisionThumbnail))
            } else {
                thumbnailIcon.setImageResource(
                    if (isFile) MimeTypeList.typeForName(name).iconResourceId
                    else R.drawable.ic_folder_list
                )
            }
            this.name.text = collisionResult.collisionName
            size.text = getSizeString(collisionResult.collisionSize!!)
            date.text = formatLongDateTime(collisionResult.collisionLastModified!!)

            val thumbnailView = if (hasThumbnail) R.id.thumbnail else R.id.thumbnail_icon

            ConstraintSet().apply {
                clone(root)
                connect(R.id.name, ConstraintSet.TOP, thumbnailView, ConstraintSet.TOP)
                applyTo(root)
            }
        }

        binding.cancelButton.text = StringResourcesUtils.getString(cancelButtonId)

        binding.cancelSeparator.isVisible = isFile
        binding.renameInfo.isVisible = isFile
        binding.renameView.optionView.isVisible = isFile
        binding.renameButton.isVisible = isFile

        if (isFile) {
            binding.renameInfo.text = StringResourcesUtils.getString(renameInfoId)
            binding.renameView.apply {
                val hasThumbnail = collisionResult.thumbnail != null
                thumbnail.isVisible = hasThumbnail
                thumbnailIcon.isVisible = !hasThumbnail
                if (hasThumbnail) {
                    thumbnail.setImageRequest(ImageRequest.fromUri(collisionResult.thumbnail))
                } else {
                    thumbnailIcon.setImageResource(
                        if (isFile) MimeTypeList.typeForName(name).iconResourceId
                        else R.drawable.ic_folder_list
                    )
                }
                this.name.text = collisionResult.renameName
                size.isVisible = false
                date.isVisible = false

                val thumbnailView = if (hasThumbnail) R.id.thumbnail else R.id.thumbnail_icon

                ConstraintSet().apply {
                    clone(root)
                    connect(
                        thumbnailView,
                        ConstraintSet.BOTTOM,
                        root.id,
                        ConstraintSet.BOTTOM
                    )
                    centerVertically(R.id.name, thumbnailView)
                    applyTo(root)
                }
            }
            binding.renameButton.text = StringResourcesUtils.getString(renameButtonId)
        }

        val pendingCollisions =
            if (isFile) viewModel.pendingFileCollisions
            else viewModel.pendingFolderCollisions

        binding.applyForAllCheck.apply {
            isVisible = pendingCollisions > 1

            if (isVisible) {
                text =
                    StringResourcesUtils.getString(R.string.file_apply_for_all, pendingCollisions)
            }
        }
    }

    /**
     * Updates the UI related to file versioning.
     *
     * @param fileVersioningInfo Triple with the following info:
     *                              - First:    True if file versioning is enabled, false otherwise.
     *                              - Second:   Collision type: upload, movement of copy.
     *                              - Third:    True if the collision is related to a file, false if is to a folder.
     */
    private fun updateFileVersioningData(fileVersioningInfo: Triple<Boolean, NameCollisionViewModel.NameCollisionType, Boolean>) {
        val isFileVersioningEnabled = fileVersioningInfo.first
        val isFile = fileVersioningInfo.third

        binding.learnMore.isVisible = isFileVersioningEnabled

        val replaceUpdateMergeInfoId: Int
        val replaceUpdateMergeButtonId: Int

        when (fileVersioningInfo.second) {
            NameCollisionViewModel.NameCollisionType.UPLOAD -> {
                replaceUpdateMergeInfoId = when {
                    !isFile -> R.string.warning_upload_and_merge
                    isFileVersioningEnabled -> R.string.warning_versioning_upload_and_update
                    else -> R.string.warning_upload_and_replace
                }
                replaceUpdateMergeButtonId = when {
                    !isFile -> R.string.upload_and_merge
                    isFileVersioningEnabled -> R.string.upload_and_update
                    else -> R.string.upload_and_replace
                }
            }
            NameCollisionViewModel.NameCollisionType.COPY -> {
                replaceUpdateMergeInfoId =
                    if (isFile) R.string.warning_copy_and_replace
                    else R.string.warning_copy_and_merge
                replaceUpdateMergeButtonId =
                    if (isFile) R.string.copy_and_replace
                    else R.string.copy_and_merge
            }
            NameCollisionViewModel.NameCollisionType.MOVEMENT -> {
                replaceUpdateMergeInfoId =
                    if (isFile) R.string.warning_move_and_replace
                    else R.string.warning_move_and_merge
                replaceUpdateMergeButtonId =
                    if (isFile) R.string.move_and_replace
                    else R.string.move_and_merge
            }
        }

        binding.replaceUpdateMergeInfo.text =
            StringResourcesUtils.getString(replaceUpdateMergeInfoId)
        binding.replaceUpdateMergeButton.text =
            StringResourcesUtils.getString(replaceUpdateMergeButtonId)
    }

    private fun manageCollisionsResolution(collisionsResolution: ArrayList<NameCollisionResult>) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(INTENT_EXTRA_COLLISION_RESULTS, collisionsResolution)
        )
    }
}