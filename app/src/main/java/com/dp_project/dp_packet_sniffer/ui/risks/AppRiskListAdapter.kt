package com.dp_project.dp_packet_sniffer.ui.risks

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.Image
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.dp_project.dp_packet_sniffer.R
import com.dp_project.hexene.localvpn.LocalVPNService.PacketInfo

class AppRiskListAdapter(
    context: Context,
    private var items: List<Map.Entry<String, List<Boolean>>>
) : ArrayAdapter<Map.Entry<String, List<Boolean>>>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (convertView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.app_list_risk_item, parent, false)
        }

        val currentItem = items[position]

        val appInfo: ApplicationInfo = context.packageManager.getApplicationInfo(currentItem.key, 0)
        val appLabel = context.packageManager.getApplicationLabel(appInfo).toString()
        val appIcon = context.packageManager.getApplicationIcon(appInfo)
        if (itemView != null){
            itemView.findViewById<ImageView>(R.id.appIcon).setImageDrawable(appIcon)
            itemView.findViewById<TextView>(R.id.appName).text = appLabel

            if (currentItem.value[0]){
                itemView.findViewById<TextView>(R.id.encryptionWarningTextView).visibility = View.VISIBLE
            }
            if (currentItem.value[1]){
                itemView.findViewById<TextView>(R.id.protocolWarningTextView).visibility = View.VISIBLE
            }
            if (currentItem.value[2]){
                itemView.findViewById<TextView>(R.id.countryWarningTextView).visibility = View.VISIBLE
            }
            if (currentItem.value[3]){
                itemView.findViewById<TextView>(R.id.trafficWarningTextView).visibility = View.VISIBLE
            }
        }
        return itemView!!
    }


}
