package com.dp_packet_sniffer.ui.Stats

import com.dp_project.dp_packet_sniffer.ui.Stats.IPCountryListAdapter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.dp_project.dp_packet_sniffer.R
import com.dp_project.dp_packet_sniffer.databinding.FragmentStatsBinding
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.DefaultValueFormatter


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
        pieChart.centerText = "Protocols used"
        pieChart.setCenterTextSize(24f)
        pieChart.minAngleForSlices = 30f


        pieChart.invalidate();

        val root: View = binding.root

        return root
    }

    private fun updatePieChart(newData: List<PieEntry>)
    {
        val pieChart = requireView().findViewById<PieChart>(R.id.pieChart)
        val dataset = PieDataSet(newData, "")
        dataset.valueFormatter = DefaultValueFormatter(0)
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

        val sharedViewModel = ViewModelProvider(requireActivity())[StatsViewModel::class.java]

        sharedViewModel.pieChartData.observe(viewLifecycleOwner, Observer { newData ->
            updatePieChart(newData)
        })

        sharedViewModel.ipCountryMap.observe(viewLifecycleOwner, Observer { map ->
            ipCountryListAdapter = IPCountryListAdapter(requireContext(), map.toList().sortedByDescending { it.second.count })
            binding.ipListView.adapter = ipCountryListAdapter
            ipCountryListAdapter.updateData(map.toList().sortedByDescending { it.second.count })
        })
    }





}