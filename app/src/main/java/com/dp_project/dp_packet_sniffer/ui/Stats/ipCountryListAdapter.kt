package com.dp_project.dp_packet_sniffer.ui.Stats

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.dp_packet_sniffer.ui.Stats.StatsViewModel
import com.dp_project.dp_packet_sniffer.R

class IPCountryListAdapter(
    context: Context,
    private var dataList: List<Pair<String, StatsViewModel.IpInfoData>>
) : ArrayAdapter<Pair<String, StatsViewModel.IpInfoData>>(context, 0, dataList) {

    fun updateData(newData: List<Pair<String, StatsViewModel.IpInfoData>>) {
        dataList = newData
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.ip_list_item, parent, false)

        val currentItem = dataList[position]

        // Display the information about IP addresses in a list
        val ipTextView = itemView.findViewById<TextView>(R.id.ipTextView)
        val countryTextView = itemView.findViewById<TextView>(R.id.countryTextView)
        val countTextView = itemView.findViewById<TextView>(R.id.countTextView)
        val topAppTextView = itemView.findViewById<TextView>(R.id.topAppTextView)
        val topProtocolTextView = itemView.findViewById<TextView>(R.id.topProtocolTextView)


        ipTextView.text = currentItem.first
        countryTextView.text = currentItem.second.country
        countTextView.text = currentItem.second.count.toString()
        try {
            val appInfo = context.packageManager.getApplicationInfo(currentItem.second.topApp, 0)
            topAppTextView.text = context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            topAppTextView.text = "Unknown"
        }
        topProtocolTextView.text = currentItem.second.topProtocol

        return itemView
    }
}

