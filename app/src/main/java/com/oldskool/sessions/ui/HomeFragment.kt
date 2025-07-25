package com.oldskool.sessions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.navigation.fragment.findNavController
import com.oldskool.sessions.R
import com.oldskool.sessions.viewmodel.HomeViewModel

class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: PostsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.postsRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        errorView = view.findViewById(R.id.errorView)

        setupRecyclerView()
        setupSwipeRefresh()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = PostsAdapter { post ->
            // Use the new Media3 player fragment
            try {
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeToPlayerDetailMedia3(
                        title = post.title ?: "No Title",
                        audioUrl = post.audioUrl ?: "",
                        imageUrl = post.featuredMediaUrl ?: ""
                    )
                )
            } catch (e: Exception) {
                // Fallback to legacy player if there's an issue
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeToPlayerDetail(
                        title = post.title ?: "No Title",
                        audioUrl = post.audioUrl ?: "",
                        imageUrl = post.featuredMediaUrl ?: ""
                    )
                )
            }
        }

        recyclerView.apply {
            layoutManager = if (resources.getBoolean(R.bool.isTablet)) {
                GridLayoutManager(context, 2)
            } else {
                LinearLayoutManager(context)
            }
            adapter = this@HomeFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                    ) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupObservers() {
        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            adapter.submitList(posts)
            errorView.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingIndicator.visibility = if (isLoading && adapter.currentList.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
            swipeRefresh.isRefreshing = isLoading && adapter.currentList.isNotEmpty()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null && adapter.currentList.isEmpty()) {
                errorView.apply {
                    text = error
                    visibility = View.VISIBLE
                }
            } else {
                errorView.visibility = View.GONE
            }
        }
    }
}
