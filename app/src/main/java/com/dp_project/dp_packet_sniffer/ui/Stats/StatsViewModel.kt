package com.dp_packet_sniffer.ui.Stats

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.dp_project.hexene.localvpn.LocalVPNService.PacketInfo
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


    /**
     * Holds information about an IP address
     */
    data class IpInfoData(
        val topApp: String,
        val topProtocol: String,
        val count: Int,
        val country: String = "Unknown"
    )

    // Map of <IpAddress> - <IpInfo>
    val ipCountryMap: MutableLiveData<Map<String, IpInfoData>> = MutableLiveData()

    // List of PieEntries ready to be displayed
    val pieChartData = MutableLiveData<List<PieEntry>>()

    private val _text = MutableLiveData<String>().apply {
        value = "This is Stats Fragment"
    }
    val text: LiveData<String> = _text

    init {
        ipCountryMap.value = mutableMapOf()
    }

    /**
     * Process packet data after scanning is completed and prepare the pieEntry entities
     */
    fun updatePieChart(newData: ArrayList<PacketInfo>) {
        val protocolCounts = HashMap<String, Int>()
        for (packetInfo in newData) {
            val protocol = packetInfo.protocol
            // Counts protocols
            protocolCounts[protocol] = protocolCounts.getOrDefault(protocol, 0) + 1
        }

        // Prepares the entries for pie chart
        val entries = ArrayList<PieEntry>()
        for ((protocol, count) in protocolCounts) {
            entries.add(PieEntry(count.toFloat().roundToInt().toFloat(), protocol))
        }

        pieChartData.value = entries

    }

    /**
     * Process the packet data into information about IP addresses
     */
    fun updateIPList(newdata: ArrayList<PacketInfo>) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val ipAppCounts =
                    mutableMapOf<String, MutableMap<String, Int>>()    // IP -> (AppName -> Count)
                val ipProtocolCounts =
                    mutableMapOf<String, MutableMap<String, Int>>() // IP -> (Protocol -> Count)
                val ipTotalCounts =
                    mutableMapOf<String, Int>()                        // IP -> Total packet count

                for (item in newdata) {
                    val ipAddress = item.destinationIP.substring(1)

                    // Update total packet count
                    ipTotalCounts[ipAddress] = (ipTotalCounts[ipAddress] ?: 0) + 1
                    // Update App counts
                    val appName = item.applicationInfo?.packageName ?: "Unknown"
                    val appCountMap = ipAppCounts.getOrPut(ipAddress) { mutableMapOf() }
                    appCountMap[appName] = (appCountMap[appName] ?: 0) + 1

                    // Update Protocol counts
                    val protocol = item.protocol ?: "Unknown"
                    val protocolCountMap = ipProtocolCounts.getOrPut(ipAddress) { mutableMapOf() }
                    protocolCountMap[protocol] = (protocolCountMap[protocol] ?: 0) + 1
                }

                // Build final map with IpInfoData
                val finalMap = mutableMapOf<String, IpInfoData>()
                for ((ip, count) in ipTotalCounts) {
                    val topApp = ipAppCounts[ip]?.maxByOrNull { it.value }?.key ?: "Unknown"
                    val topProtocol =
                        ipProtocolCounts[ip]?.maxByOrNull { it.value }?.key ?: "Unknown"
                    finalMap[ip] = IpInfoData(topApp, topProtocol, count)
                }

                // Send update
                ipCountryMap.postValue(finalMap)

                // Fetch geolocation info
                val response = getGeoLocations(newdata)
                parseResponse(response)
            } catch (e: Exception) {
                Log.e("GeoLocation", "Error: ${e.message}")
            }
        }
    }


    /**
     * Fetches information about the location of every IP address captured through an external API
     */
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

    /**
     * Process the response from geolocation API
     */
    private fun parseResponse(response: String) {
        val jsonArray = JSONArray(response)
        val currentMap = ipCountryMap.value?.toMutableMap() ?: return

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val ipAddress = jsonObject.optString("query", "")
            val country = jsonObject.optString("country", "")

            if (ipAddress.isNotEmpty() && country.isNotEmpty()) {
                val oldData = currentMap[ipAddress]
                if (oldData != null) {
                    // Update the country field, keep topApp, topProtocol, and count
                    val updatedData = oldData.copy(country = country)
                    currentMap[ipAddress] = updatedData
                }
            }
        }

        ipCountryMap.postValue(currentMap)
    }

}