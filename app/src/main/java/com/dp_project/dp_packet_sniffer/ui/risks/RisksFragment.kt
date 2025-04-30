package com.dp_project.dp_packet_sniffer.ui.risks

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.dp_packet_sniffer.MainActivity
import com.dp_project.dp_packet_sniffer.databinding.FragmentRisksBinding


class RisksFragment : Fragment() {

    private var _binding: FragmentRisksBinding? = null

    private lateinit var appRiskListAdapter: AppRiskListAdapter
    private lateinit var listView: ListView


    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRisksBinding.inflate(inflater, container, false)
        val root: View = binding.root
        listView = binding.appRisksList


        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appWarnings = (activity as MainActivity).appWarnings
        listView.adapter = AppRiskListAdapter(requireContext(), appWarnings.entries.toList())

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

