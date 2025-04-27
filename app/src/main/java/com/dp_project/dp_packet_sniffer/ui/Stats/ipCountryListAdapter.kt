import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.dp_project.dp_packet_sniffer.R

class IPCountryListAdapter(context: Context, private var dataList: List<Pair<String, Pair<String, Int>>>) :
    ArrayAdapter<Pair<String, Pair<String, Int>>>(context, 0, dataList) {


    fun updateData(newData: List<Pair<String, Pair<String, Int>>>) {

        dataList = newData
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.ip_list_item, parent, false)
        }
//        if (position >= dataList.size)
//        {
//            return itemView!!
//        }
        val currentItem = dataList[position]

        val ipTextView = itemView!!.findViewById<TextView>(R.id.ipTextView)
        val countryTextView = itemView.findViewById<TextView>(R.id.countryTextView)
        val countTextView = itemView.findViewById<TextView>(R.id.countTextView)

        ipTextView.text = currentItem.first
        countryTextView.text = currentItem.second.first
        countTextView.text = currentItem.second.second.toString()
        return itemView
    }

}
