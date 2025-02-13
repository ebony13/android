package mega.privacy.android.app.meeting.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.FragmentChatTabsBinding
import mega.privacy.android.app.main.ManagerActivity
import mega.privacy.android.app.main.megachat.RecentChatsFragment
import mega.privacy.android.app.meeting.chats.adapter.ChatTabsPageAdapter
import mega.privacy.android.app.meeting.chats.adapter.ChatTabsPageAdapter.Tabs.CHAT
import mega.privacy.android.app.meeting.list.MeetingListFragment
import mega.privacy.android.app.presentation.chat.dialog.AskForDisplayOverActivity
import mega.privacy.android.app.utils.ColorUtils.setElevationWithColor
import mega.privacy.android.app.utils.StringResourcesUtils

/**
 * Chat tabs fragment containing Chat and Meeting fragment
 */
@AndroidEntryPoint
class ChatTabsFragment : Fragment() {

    companion object {
        private const val STATE_PAGER_POSITION = "STATE_PAGER_POSITION"

        @JvmStatic
        fun newInstance(): ChatTabsFragment =
            ChatTabsFragment()
    }

    private lateinit var binding: FragmentChatTabsBinding

    private var skipClearSelection = false
    private val toolbarElevation by lazy { resources.getDimension(R.dimen.toolbar_elevation) }
    private val tabSelectedListener by lazy { buildTabSelectedListener() }
    private val pageChangeCallback by lazy { buildPageChangeCallback() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentChatTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        this.setupView()
        startActivity(Intent(requireContext(), AskForDisplayOverActivity::class.java))
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState?.containsKey(STATE_PAGER_POSITION) == true) {
            skipClearSelection = true
            val position = savedInstanceState.getInt(STATE_PAGER_POSITION)
            binding.pager.setCurrentItem(position, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PAGER_POSITION, binding.pager.currentItem)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.tabs.removeOnTabSelectedListener(tabSelectedListener)
        binding.pager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }

    private fun setupView() {
        binding.tabs.addOnTabSelectedListener(tabSelectedListener)
        binding.pager.apply {
            adapter = ChatTabsPageAdapter(this@ChatTabsFragment)

            TabLayoutMediator(binding.tabs, this) { tab, position ->
                tab.text = if (position == CHAT.ordinal) {
                    StringResourcesUtils.getString(R.string.chats_label)
                } else {
                    StringResourcesUtils.getString(R.string.chat_tab_meetings_title)
                }
            }.attach()

            registerOnPageChangeCallback(pageChangeCallback)
        }

        binding.root.post {
            (activity as? ManagerActivity?)?.showHideBottomNavigationView(false)
            (activity as? ManagerActivity?)?.showFabButton()
            (activity as? ManagerActivity?)?.invalidateOptionsMenu()
        }
    }

    private fun buildTabSelectedListener() = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {}
        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabReselected(tab: TabLayout.Tab?) {
            scrollToTop()
        }
    }

    private fun buildPageChangeCallback() = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            (activity as? ManagerActivity?)?.closeSearchView()

            if (!skipClearSelection) {
                childFragmentManager.fragments.forEach { fragment ->
                    when (fragment) {
                        is RecentChatsFragment -> fragment.clearSelections()
                        is MeetingListFragment -> fragment.clearSelections(true)
                    }
                }
            } else {
                skipClearSelection = false
            }
        }
    }

    /**
     * Make children fragment scroll to top of the list
     */
    private fun scrollToTop() {
        childFragmentManager.fragments.firstOrNull { it.isResumed }?.let { fragment ->
            when (fragment) {
                is RecentChatsFragment -> fragment.scrollToTop()
                is MeetingListFragment -> fragment.scrollToTop()
            }
        }
    }

    /**
     * Set search query
     *
     * @param query Search query string
     */
    fun setSearchQuery(query: String) {
        childFragmentManager.fragments.firstOrNull { it.isResumed }?.let { fragment ->
            when (fragment) {
                is RecentChatsFragment -> fragment.filterChats(query, false)
                is MeetingListFragment -> fragment.setSearchQuery(query)
            }
        }
    }

    /**
     * Show toolbar elevation
     *
     * @param show  Flag to either show or hide toolbar elevation
     */
    fun showElevation(show: Boolean) {
        binding.tabs.setElevationWithColor(if (show) toolbarElevation else 0F)
    }

    /**
     * Get existing RecentChatsFragment
     *
     * @return  RecentChatsFragment
     */
    fun getRecentChatsFragment(): RecentChatsFragment? =
        childFragmentManager.fragments.find { it is RecentChatsFragment } as? RecentChatsFragment?
}
