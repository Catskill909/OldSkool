package com.oldskool.sessions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.oldskool.sessions.databinding.FragmentArchivesBinding
import com.oldskool.sessions.viewmodel.ArchivesViewModel

class ArchivesFragment : Fragment() {
    private var _binding: FragmentArchivesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ArchivesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArchivesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ArchivesViewModel::class.java]
        
        // Observe WordPress data
        viewModel.wordPressData.observe(viewLifecycleOwner) { _ ->
            // TODO: Update UI with WordPress posts
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
