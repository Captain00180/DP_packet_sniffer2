package com.dp_packet_sniffer.ui.Stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dp_project.dp_packet_sniffer.databinding.DataUsageBinding

class DataUsageFragment : Fragment() {
    private var _binding: DataUsageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DataUsageBinding.inflate(inflater, container, false)
        val view = binding.root




        return view
    }
}