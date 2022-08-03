package mega.privacy.android.app.imageviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.FragmentImageViewerBinding
import mega.privacy.android.app.imageviewer.adapter.ImageViewerAdapter
import mega.privacy.android.app.imageviewer.data.ImageItem
import mega.privacy.android.app.imageviewer.util.shouldShowDownloadOption
import mega.privacy.android.app.imageviewer.util.shouldShowForwardOption
import mega.privacy.android.app.imageviewer.util.shouldShowManageLinkOption
import mega.privacy.android.app.imageviewer.util.shouldShowSendToContactOption
import mega.privacy.android.app.imageviewer.util.shouldShowShareOption
import mega.privacy.android.app.utils.ContextUtils.isLowMemory
import mega.privacy.android.app.utils.StringResourcesUtils
import mega.privacy.android.app.utils.ViewUtils.waitForLayout
import timber.log.Timber

@AndroidEntryPoint
class ImageViewerFragment : Fragment() {

    private lateinit var binding: FragmentImageViewerBinding

    private val viewModel by activityViewModels<ImageViewerViewModel>()
    private val pagerAdapter by lazy { ImageViewerAdapter(this) }

    private var pageCallbackSet = false
    private val pageChangeCallback by lazy {
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.updateCurrentPosition(position, false)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupView()
        setupObservers()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_image_viewer, menu)

        val imageItem = viewModel.getCurrentImageItem() ?: return
        menu.apply {
            findItem(R.id.action_forward)?.isVisible =
                imageItem.shouldShowForwardOption()
//                            && !isFileVersion
            findItem(R.id.action_share)?.isVisible =
                imageItem is ImageItem.ChatNode && imageItem.shouldShowShareOption()
//                            && !isFileVersion
            findItem(R.id.action_download)?.isVisible = imageItem.shouldShowDownloadOption()
            findItem(R.id.action_get_link)?.isVisible =
                imageItem.shouldShowManageLinkOption()
//                            && !isFileVersion
            findItem(R.id.action_send_to_chat)?.isVisible =
                imageItem.shouldShowSendToContactOption(viewModel.isUserLoggedIn())
//                            && !isFileVersion
            findItem(R.id.action_more)?.isVisible = imageItem.nodeItem != null
//                        && !isFileVersion
        }
    }

    override fun onLowMemory() {
        if (binding.viewPager.offscreenPageLimit != ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT) {
            binding.viewPager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        }
        super.onLowMemory()
    }

    override fun onDestroyView() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }

    private fun setupView() {
        binding.viewPager.apply {
            isSaveEnabled = false
            offscreenPageLimit = if (requireContext().isLowMemory()) {
                ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
            } else {
                ImageViewerActivity.IMAGE_OFFSCREEN_PAGE_LIMIT
            }
            setPageTransformer(MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.image_viewer_pager_margin)))
            adapter = pagerAdapter
        }

        binding.motion.post { binding.motion.transitionToEnd() }
    }

    private fun setupObservers() {
        viewModel.onImagesIds().observe(viewLifecycleOwner) { items ->
            if (items.isNullOrEmpty()) {
                Timber.e("Null or empty image items")
                activity?.finish()
            } else {
                binding.viewPager.waitForLayout {
                    val sizeDifference = pagerAdapter.itemCount != items.size
                    pagerAdapter.submitList(items) {
                        if (sizeDifference) {
                            pagerAdapter.notifyDataSetChanged()
                        }
                    }
                    true
                }

                val currentPosition = viewModel.getCurrentPosition()
                val imagesSize = items.size
                binding.txtPageCount.apply {
                    text = StringResourcesUtils.getString(
                        R.string.wizard_steps_indicator,
                        currentPosition + 1,
                        imagesSize
                    )
                    isVisible = imagesSize > 1
                }
            }
            binding.progress.hide()
        }

        viewModel.onCurrentPosition().observe(viewLifecycleOwner) { position ->
            binding.viewPager.apply {
                waitForLayout {
                    if (currentItem != position) {
                        setCurrentItem(position, pageCallbackSet)
                    }

                    if (!pageCallbackSet) {
                        pageCallbackSet = true
                        registerOnPageChangeCallback(pageChangeCallback)
                    }
                    true
                }
            }

            binding.txtPageCount.apply {
                val imagesSize = viewModel.getImagesSize()
                text = StringResourcesUtils.getString(
                    R.string.wizard_steps_indicator,
                    position + 1,
                    imagesSize
                )
                isVisible = imagesSize > 1
            }
        }

        viewModel.onCurrentImageItem().observe(viewLifecycleOwner, ::showCurrentImageInfo)
        viewModel.onShowToolbar().observe(viewLifecycleOwner, ::changeBottomBarVisibility)
    }

    /**
     * Populate current image information to bottom texts and toolbar options.
     *
     * @param imageItem  Image item to show
     */
    private fun showCurrentImageInfo(imageItem: ImageItem?) {
        binding.txtTitle.text = imageItem?.name
        activity?.invalidateOptionsMenu()
    }

    /**
     * Change bottomBar visibility with animation.
     *
     * @param show                  Show or hide toolbar/bottombar
     * @param enableTransparency    Enable transparency change
     */
    private fun changeBottomBarVisibility(show: Boolean, enableTransparency: Boolean = false) {
        binding.motion.post {
            val color: Int
            if (show) {
                color = R.color.white_black
                binding.motion.transitionToEnd()
            } else {
                color = android.R.color.black
                binding.motion.transitionToStart()
            }
            binding.motion.setBackgroundColor(ContextCompat.getColor(requireContext(),
                if (enableTransparency && !show) {
                    android.R.color.transparent
                } else {
                    color
                }))
        }
    }
}
