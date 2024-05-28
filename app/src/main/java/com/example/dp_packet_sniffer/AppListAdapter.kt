import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.dp_packet_sniffer.AppInfoCheckbox
import com.example.dp_packet_sniffer.R
class AppListAdapter(context: Context, private var apps: List<AppInfoCheckbox>, private val packageManager: PackageManager) :
    ArrayAdapter<AppInfoCheckbox>(context, R.layout.mylist, apps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        val viewHolder: ViewHolder


        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.mylist, parent, false)
            viewHolder = ViewHolder(
                itemView.findViewById(R.id.appIcon),
                itemView.findViewById(R.id.appName),
                itemView.findViewById(R.id.appCheckbox)
            )
            itemView.tag = viewHolder
        } else {
            viewHolder = itemView.tag as ViewHolder
        }

        val appInfoCheckbox = getItem(position)
        if (appInfoCheckbox != null)
        {
            viewHolder.appIcon.setImageDrawable(appInfoCheckbox.applicationInfo.loadIcon(packageManager))
            viewHolder.appName.text = appInfoCheckbox.applicationInfo.loadLabel(packageManager).toString()
            viewHolder.appCheckbox.setOnCheckedChangeListener(null)
            viewHolder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                apps[position].isChecked = isChecked
            }
            viewHolder.appCheckbox.isChecked = appInfoCheckbox.isChecked
        }



        return itemView!!

    }

    private class ViewHolder(
        val appIcon: ImageView,
        val appName: TextView,
        val appCheckbox: CheckBox
    )
}
