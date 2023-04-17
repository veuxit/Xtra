package com.github.andreyasadchy.xtra.ui.search.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.databinding.FragmentSearchTagsBinding
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.Utils
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.showKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TagSearchFragment : PagedListFragment() {

    private var _binding: FragmentSearchTagsBinding? = null
    private val binding get() = _binding!!
    private val args: TagSearchFragmentArgs by navArgs()
    private val viewModel: TagSearchViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<Tag, out RecyclerView.ViewHolder>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = TagSearchAdapter(this, args)
        setAdapter(binding.recyclerViewLayout.recyclerView, pagingAdapter)
        val activity = requireActivity() as MainActivity
        with(binding) {
            toolbar.apply {
                navigationIcon = Utils.getNavigationIcon(activity)
                setNavigationOnClickListener { activity.popFragment() }
            }
            search.showKeyboard()
        }
    }

    override fun initialize() {
        with(binding.recyclerViewLayout) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.flow.collectLatest { pagingData ->
                    pagingAdapter.submitData(pagingData)
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                pagingAdapter.loadStateFlow.collectLatest { loadState ->
                    progressBar.isVisible = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount == 0
                    nothingHere.isVisible = loadState.refresh !is LoadState.Loading && pagingAdapter.itemCount == 0 && viewModel.query.value.isNotBlank()
                }
            }
        }
        with(binding) {
            search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                private var job: Job? = null

                override fun onQueryTextSubmit(query: String): Boolean {
                    search(query)
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    job?.cancel()
                    if (newText.isNotEmpty()) {
                        job = lifecycleScope.launchWhenResumed {
                            delay(750)
                            search(newText)
                        }
                    } else {
                        search(newText) //might be null on rotation, so as?
                    }
                    return false
                }
            })
        }
    }

    private fun search(query: String) {
        viewModel.setQuery(query)
    }

    override fun onNetworkRestored() {
        pagingAdapter.retry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
