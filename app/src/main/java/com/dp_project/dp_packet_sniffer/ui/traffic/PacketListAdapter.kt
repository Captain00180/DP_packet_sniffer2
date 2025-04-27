package com.dp_project.dp_packet_sniffer.ui.traffic

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.content.pm.PackageManager
import com.dp_project.dp_packet_sniffer.R
import com.dp_project.hexene.localvpn.LocalVPNService.PacketInfo

class PacketListAdapter(context: Context, private var items: List<PacketInfo>) : ArrayAdapter<PacketInfo>(context, 0, items) {

    fun updateData(newData: List<PacketInfo>)
    {
        items = newData
        notifyDataSetChanged()
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.packet_detail, parent, false)
        }

        val currentItem = items[position]

        // Timestamp
        var timestamp = currentItem.timestamp.toString()
        itemView?.findViewById<TextView>(R.id.timestampTextView)?.text = timestamp.substring(0, timestamp.length - 15)
        // Protocol
        itemView?.findViewById<TextView>(R.id.protocolTextView)?.text = "Protocol: " + currentItem.protocol
        // Destination IP
        itemView?.findViewById<TextView>(R.id.destinationIpTextView)?.text = "IP: " + currentItem.destinationIP.substring(1)
        // Payload Size
        itemView?.findViewById<TextView>(R.id.payloadSizeTextView)?.text = "Payload Size: " + currentItem.payloadSize.toString() + "B"
        // App icon
        if (currentItem.applicationInfo != null) {
            itemView?.findViewById<ImageView>(R.id.appIcon)?.setImageDrawable(currentItem.applicationInfo.loadIcon(context.packageManager))
            itemView?.findViewById<TextView>(R.id.appName)?.text = currentItem.applicationInfo.loadLabel(context.packageManager).toString()
        }


        return itemView!!
    }


}
