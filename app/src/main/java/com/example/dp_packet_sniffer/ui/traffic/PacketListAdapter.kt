package com.example.dp_packet_sniffer.ui.traffic

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.dp_packet_sniffer.AppInfoCheckbox
import com.example.dp_packet_sniffer.R
import com.example.hexene.localvpn.LocalVPNService.PacketInfo
import org.w3c.dom.Text

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
        itemView?.findViewById<TextView>(R.id.payloadSizeTextView)?.text = "Payload Size:" + currentItem.payloadSize.toString()

        return itemView!!
    }


}
