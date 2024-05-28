package com.dp_packet_sniffer.ui.Stats

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.hexene.localvpn.LocalVPNService.PacketInfo
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class StatsViewModel : ViewModel() {

    val ipCountryMap: MutableLiveData<Map<String, Pair<String, Int>>> = MutableLiveData()

    val pieChartData = MutableLiveData<List<PieEntry>>()

    private val _text = MutableLiveData<String>().apply {
        value = "This is Stats Fragment"
    }
    val text: LiveData<String> = _text

    init {
        ipCountryMap.value = mutableMapOf()
    }

    fun updatePieChart(newData: ArrayList<PacketInfo>)
    {

        val protocolCounts = HashMap<String, Int>()
        for (packetInfo in newData) {
            val protocol = packetInfo.protocol
            protocolCounts[protocol] = protocolCounts.getOrDefault(protocol, 0) + 1
        }

        val entries = ArrayList<PieEntry>()
        for ((protocol, count) in protocolCounts) {
            entries.add(PieEntry(count.toFloat().roundToInt().toFloat(), protocol))
        }

        pieChartData.value = entries

    }

    fun updateIPList(newdata: ArrayList<PacketInfo>)
    {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val newMap = mutableMapOf<String, Pair<String, Int>>()
                for (item in newdata) {
                    val ipAddress = item.destinationIP.substring(1)
                    if (newMap.containsKey(ipAddress))
                    {
                        val oldPair = newMap[ipAddress]
                        newMap[ipAddress] = Pair("NULL", oldPair?.second?.plus(1) ?: 1)
                    }
                    else
                    {
                        newMap[ipAddress] = Pair("NULL", 1)
                    }
                }
                ipCountryMap.postValue(newMap.toMutableMap())
                val response = getGeoLocations(newdata)
                parseResponse(response)
            } catch (e: Exception) {
                Log.e("GeoLocation", "Error: ${e.message}")
            }
            val x = 3
        }
    }

    private fun getGeoLocations(newdata: ArrayList<PacketInfo>): String {
        val url = URL("http://ip-api.com/batch")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val uniqueIPs = newdata.map { entry ->
                entry.destinationIP.substring(1)
            }.distinct()

            val jsonArray = JSONArray(uniqueIPs.map { ip ->
                JSONObject().apply {
                    put("query", ip)
                }
            })
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonArray.toString())
            writer.flush()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                return reader.use(BufferedReader::readText)
            } else {
                throw Exception("HTTP error code: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: String) {
        val jsonArray = JSONArray(response)
        val newMap = ipCountryMap.value!!.toMutableMap()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val ipAddress = jsonObject.optString("query", "")
            val country = jsonObject.optString("country", "")
            if (ipAddress.isNotEmpty() && country.isNotEmpty()) {
//                if (newMap.containsKey(ipAddress))
//                {
//                    // Increase count
//                    var oldPair = newMap[ipAddress]
//                    newMap[ipAddress] = Pair(country, (oldPair?.second ?: 0) + 1)
//                }
//                else
//                {
//                    newMap[ipAddress] = Pair(country, 1)
//                }
                val oldCount = newMap[ipAddress]?.second
                newMap[ipAddress] = Pair(country, oldCount!!)
            }
        }
        ipCountryMap.postValue(newMap.toMutableMap())
        val x = 3
        Log.d("Done", "Done")
    }
}