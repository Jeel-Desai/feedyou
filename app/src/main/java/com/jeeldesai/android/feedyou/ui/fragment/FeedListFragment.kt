package com.jeeldesai.android.feedyou.ui.fragment

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.jeeldesai.android.feedyou.R
import com.jeeldesai.android.feedyou.data.local.FeedPreferences
import com.jeeldesai.android.feedyou.databinding.FragmentFeedListBinding
import com.jeeldesai.android.feedyou.ui.adapter.FeedListAdapter
import com.jeeldesai.android.feedyou.ui.viewmodel.FeedListViewModel
import com.jeeldesai.android.feedyou.util.extensions.addRipple
import com.jeeldesai.android.feedyou.util.extensions.setSimpleVisibility

class FeedListFragment: VisibleFragment(), FeedListAdapter.OnItemClickListener {

    interface Callbacks {

        fun onMenuItemSelected(item: Int)

        fun onFeedSelected(feedId: String, activeFeedId: String?)
    }

    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FeedListViewModel
    lateinit var adapter: FeedListAdapter
    private var callbacks: Callbacks? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as Callbacks?
        adapter = FeedListAdapter(context, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(FeedListViewModel::class.java)
        viewModel.setFeedOrder(FeedPreferences.feedListOrder)
        viewModel.setMinimizedCategories(FeedPreferences.minimizedCategories)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedListBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.feedListLiveData.observe(viewLifecycleOwner, { feeds ->
            adapter.submitList(feeds)
            val isEmpty = feeds.isEmpty()
            if (isEmpty) updateActiveFeedId(null)
            binding.newEntriesButton.setSimpleVisibility(!isEmpty)
            binding.starredEntriesButton.setSimpleVisibility(!isEmpty)
            binding.manageButton.setSimpleVisibility(!isEmpty)
            binding.bottomDivider.setSimpleVisibility(!isEmpty)
        })
    }

    override fun onStart() {
        super.onStart()

        binding.manageButton.setOnClickListener {
            callbacks?.onMenuItemSelected(ITEM_MANAGE_FEEDS)
        }

        binding.addButton.setOnClickListener {
            callbacks?.onMenuItemSelected(ITEM_ADD_FEEDS)
        }

        binding.newEntriesButton.setOnClickListener {
            callbacks?.onFeedSelected(EntryListFragment.FOLDER_NEW, viewModel.activeFeedId)
        }

        binding.starredEntriesButton.setOnClickListener {
            callbacks?.onFeedSelected(EntryListFragment.FOLDER_STARRED, viewModel.activeFeedId)
        }

        binding.settingsButton.setOnClickListener {
            callbacks?.onMenuItemSelected(ITEM_SETTINGS)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setFeedOrder(FeedPreferences.feedListOrder)
    }

    override fun onFeedSelected(feedId: String) {
        resetFolderHighlights()
        callbacks?.onFeedSelected(feedId, viewModel.activeFeedId)
        viewModel.activeFeedId = feedId
        Handler(Looper.getMainLooper()).postDelayed({
            binding.recyclerView.adapter = adapter
        }, 500)
    }

    override fun onCategoryClicked(category: String) {
        viewModel.toggleCategoryDropDown(category)
    }

    fun updateActiveFeedId(feedId: String?) {
        resetFolderHighlights()
        viewModel.activeFeedId = feedId
        adapter.setActiveFeedId(feedId)
        binding.recyclerView.adapter = adapter

        context?.let { context ->
            val color = ContextCompat.getColor(context, R.color.colorSelect)
            if (feedId == EntryListFragment.FOLDER_NEW) {
                binding.newEntriesButton.setBackgroundColor(color)
            } else if (feedId == EntryListFragment.FOLDER_STARRED) {
                binding.starredEntriesButton.setBackgroundColor(color)
            }
        }
    }

    private fun resetFolderHighlights() {
        binding.starredEntriesButton.addRipple()
        binding.newEntriesButton.addRipple()
    }

    fun getCategories(): Array<String> {
        return viewModel.categories
    }

    override fun onStop() {
        super.onStop()
        FeedPreferences.minimizedCategories = viewModel.minimizedCategories
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

    companion object {

        const val ITEM_MANAGE_FEEDS = 0
        const val ITEM_ADD_FEEDS = 1
        const val ITEM_SETTINGS = 2

        fun newInstance(): FeedListFragment = FeedListFragment()
    }
}