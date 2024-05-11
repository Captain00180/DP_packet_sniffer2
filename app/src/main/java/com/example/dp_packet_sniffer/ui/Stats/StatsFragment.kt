package com.dp_packet_sniffer.ui.Stats

import IPCountryListAdapter
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.dp_packet_sniffer.R
import com.example.dp_packet_sniffer.databinding.FragmentStatsBinding
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry


class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null

    private lateinit var ipCountryListAdapter: IPCountryListAdapter

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val statsViewModel =
            ViewModelProvider(this).get(StatsViewModel::class.java)

        _binding = FragmentStatsBinding.inflate(inflater, container, false)


        var pieChart = _binding!!.pieChart

        pieChart.isDrawHoleEnabled = true
        pieChart.setUsePercentValues(true)
        pieChart.centerText = "Protocols used"
        pieChart.setCenterTextSize(24f)

        pieChart.invalidate();

        val root: View = binding.root

        return root
    }

    private fun updatePieChart(newData: List<PieEntry>)
    {
        val pieChart = requireView().findViewById<PieChart>(R.id.pieChart)
        val dataset = PieDataSet(newData, "")
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#304567"))
        colors.add(Color.parseColor("#309967"))
        colors.add(Color.parseColor("#476567"))
        colors.add(Color.parseColor("#890567"))
        colors.add(Color.parseColor("#a35567"))
        colors.add(Color.parseColor("#ff5f67"))
        colors.add(Color.parseColor("#3ca567"))
        dataset.setColors(colors)
        val data = PieData(dataset)
        data.setValueTextSize(12f);
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the ViewModel
        val sharedViewModel = ViewModelProvider(requireActivity())[StatsViewModel::class.java]

        // Observe changes to the pieChartData
        sharedViewModel.pieChartData.observe(viewLifecycleOwner, Observer { newData ->
            // Update the pie chart with the new data
            updatePieChart(newData)
        })

        sharedViewModel.ipCountryMap.observe(viewLifecycleOwner, Observer { map ->
            // Update the adapter data with the new map values
            ipCountryListAdapter = IPCountryListAdapter(requireContext(), map.toList().sortedByDescending { it.second.second })
            binding.ipListView.adapter = ipCountryListAdapter
            ipCountryListAdapter.updateData(map.toList().sortedByDescending { it.second.second })
        })
    }



}