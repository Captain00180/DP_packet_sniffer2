package com.dp_packet_sniffer.ui.traffic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.dp_packet_sniffer.MainActivity
import com.dp_project.dp_packet_sniffer.databinding.FragmentTrafficBinding
import com.dp_project.dp_packet_sniffer.ui.traffic.PacketListAdapter


class TrafficFragment : Fragment() {

    private var _binding: FragmentTrafficBinding? = null

    private lateinit var packetListAdapter: PacketListAdapter
    private lateinit var listView: ListView


    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentTrafficBinding.inflate(inflater, container, false)
        val root: View = binding.root
        listView = binding.trafficListView

        binding.buttonSortByApp.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val packetData = (activity as MainActivity).packetData
                packetData.sortBy { it.applicationInfo?.name ?: "zzzzzzzzzz" }
                listView.adapter = PacketListAdapter(requireContext(), packetData)
            }
        })

        binding.buttonSortByTime.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val packetData = (activity as MainActivity).packetData
                packetData.sortBy { it.timestamp }
                listView.adapter = PacketListAdapter(requireContext(), packetData)
            }
        })

        binding.buttonSortByProtocol.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val packetData = (activity as MainActivity).packetData
                packetData.sortBy { it.protocol }
                listView.adapter = PacketListAdapter(requireContext(), packetData)
            }
        })

        binding.buttonSortByIP.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val packetData = (activity as MainActivity).packetData
                packetData.sortBy { it.destinationIP }
                listView.adapter = PacketListAdapter(requireContext(), packetData)
            }
        })
        binding.buttonSortBySize.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val packetData = (activity as MainActivity).packetData
                packetData.sortBy { it.payloadSize }
                listView.adapter = PacketListAdapter(requireContext(), packetData)
            }
        })

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Display raw packet data
        val packetData = (activity as MainActivity).packetData
        packetData.sortBy { it.timestamp }
        listView.adapter = PacketListAdapter(requireContext(), packetData)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

