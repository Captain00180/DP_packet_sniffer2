package com.dp_packet_sniffer

import AppListAdapter
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.dp_packet_sniffer.ui.Stats.StatsViewModel
import com.dp_project.dp_packet_sniffer.R
import com.dp_project.dp_packet_sniffer.databinding.ActivityMainBinding
import com.dp_project.hexene.localvpn.LocalVPNService
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.log

data class AppInfoCheckbox(
    val applicationInfo: ApplicationInfo,
    var isChecked: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val allApps = mutableListOf<AppInfoCheckbox>()

    private val vpnInitialized = false

    private val VPN_REQUEST_CODE = 0x0F

    private var waitingForVPNStart = false

    private var vpnIntent : Intent? = null

    private var vpnService: LocalVPNService? = null

    private var vpnServiceBound = false

    private val vpnStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (LocalVPNService.BROADCAST_VPN_STATE == intent.action) {
                if (intent.getBooleanExtra("running", false)) waitingForVPNStart = false
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocalVPNService.LocalBinder
            vpnService = binder.service
            vpnServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vpnServiceBound = false
        }
    }

    private lateinit var statsViewModel: StatsViewModel

    lateinit var packetData: ArrayList<LocalVPNService.PacketInfo>
    var appWarnings: MutableMap<String, List<Boolean>> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val listView: ListView = findViewById(R.id.applist)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_stats, R.id.navigation_traffic
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        //Disable navigations
        for (i in 0 until navView.menu.size()) {
            navView.menu.getItem(i).isEnabled = false
            val disabledColor = Color.parseColor("#A0A0A0")
            navView.itemIconTintList = ColorStateList.valueOf(disabledColor)
            navView.itemTextColor = ColorStateList.valueOf(disabledColor)
        }

        getallapps(null, listView)

        val adapter = AppListAdapter(this, allApps, packageManager)

        listView.adapter = adapter

        var selectAllCheckbox = findViewById<CheckBox>(R.id.selectAllCheckbox)
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            allApps.forEach { it.isChecked = isChecked }
            adapter.notifyDataSetChanged()
        }

        statsViewModel = ViewModelProvider(this)[StatsViewModel::class.java]

        waitingForVPNStart = false
        LocalBroadcastManager.getInstance(this).registerReceiver(
            vpnStateReceiver,
            IntentFilter(LocalVPNService.BROADCAST_VPN_STATE)
        )

        statsViewModel.ipCountryMap.observe(this) { updatedMap ->
            // React to updates here
            Log.d("IPCountryMap", "Updated map: $updatedMap")
            onIpCountryMapUpdated(updatedMap)
        }
    }

    fun getallapps(view: View?, listView: ListView) {
        val infos = packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter { info ->
            info.category != ApplicationInfo.CATEGORY_UNDEFINED }

        for (info in infos) {
            allApps.add(AppInfoCheckbox(info, false))
        }
    }

    private fun startVPN() {
        if (vpnInitialized)
        {
            return
        }
        val localVpnIntent = VpnService.prepare(this)
        if (localVpnIntent != null) startActivityForResult(
            localVpnIntent,
            VPN_REQUEST_CODE
        ) else onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            waitingForVPNStart = true
            val packageNameList: ArrayList<String> = ArrayList()
            for (allApp in allApps) {
                if (allApp.isChecked)
                {
                    packageNameList.add(allApp.applicationInfo.packageName)
                }
            }
            val intent = Intent(this,LocalVPNService::class.java )
            vpnIntent = intent
            intent.putStringArrayListExtra("allowedApplications", packageNameList)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
            startService(intent)
        }
    }

    fun startScan(view: View) {
        if(vpnIntent == null)
        {
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(Color.parseColor("#FE2727")))
            view.rootView.findViewById<TextView>(R.id.textView).text = "Stop scan"
            startVPN()
            LocalVPNService.setRunning(true)
        } else
        {
            // Stop scan
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(Color.parseColor("#27FEB3")))
            val activity = view.context as Activity

            val textView = activity.findViewById<TextView>(R.id.textView)
            val checkBox = activity.findViewById<CheckBox>(R.id.selectAllCheckbox)

            textView.text = "Scan complete"

            checkBox.visibility = View.GONE
            view.visibility = View.GONE

            // Re-enable navigation
            val navView: BottomNavigationView = binding.navView

            for (i in 1 until navView.menu.size()) {
                val item = navView.menu.getItem(i)
                item.isEnabled = true
            }
            val disabledColor = Color.parseColor("#4a4a4a")
            navView.itemIconTintList = ColorStateList.valueOf(disabledColor)
            navView.itemTextColor = ColorStateList.valueOf(disabledColor)

            Log.d("Service running", LocalVPNService.isRunning().toString())
            var ret = vpnService?.stopVPNService()
            if (ret != null) {
                packetData = ret
            }
            unbindService(connection)
            vpnServiceBound = false
            vpnIntent = null
            Log.d("Service running", LocalVPNService.isRunning().toString())
            analyzeData()
        }

    }


    fun analyzeData() {
        // Group packets by app package name
        val packetsByApp = packetData
            .filter { it.applicationInfo != null && it.applicationInfo.packageName != null }
            .groupBy { it.applicationInfo.packageName!! }

        // Map of app to total payload size
        val appTraffic = packetsByApp.mapValues { (_, packets) ->
            packets.sumOf { it.payloadSize }
        }

        // Sort apps by traffic
        val sortedAppsByTraffic = appTraffic.entries.sortedByDescending { it.value }

        // Detect if the top app has 3x more traffic than the second
        val topApp = sortedAppsByTraffic.getOrNull(0)
        val secondApp = sortedAppsByTraffic.getOrNull(1)
        if (topApp != null && secondApp != null && topApp.value >= 3 * secondApp.value) {
            Log.w("RiskAnalysis", "App ${topApp.key} has 3x more traffic than second top app")
        }

        // Analyze each app individually
        for ((packageName, packets) in packetsByApp) {
            var encryptionWarning = false
            var protocolWarning = false
            var countryWarning = false
            val trafficWarning = topApp != null && secondApp != null && topApp.value >= 3 * secondApp.value && topApp.key == packageName

            val total = packets.size
            val unencryptedCount = packets.count { it.protocol == "HTTPS(443)" }
            val otherProtocolCount = packets.count { it.protocol == "Other" }

            val unencryptedRatio = unencryptedCount.toDouble() / total
            val otherProtocolRatio = otherProtocolCount.toDouble() / total

            if (unencryptedRatio > 0.5) {
                encryptionWarning = true
            }

            if (otherProtocolRatio > 0.5) {
                protocolWarning = true
            }

            appWarnings[packageName] = mutableListOf(encryptionWarning, protocolWarning, countryWarning, trafficWarning)
        }

        statsViewModel.updatePieChart(packetData)
        statsViewModel.updateIPList(packetData)

        val geoMap = statsViewModel.ipCountryMap
        Log.d("Debug", geoMap.value.toString())

    }

    private fun onIpCountryMapUpdated(updatedMap: Map<String, StatsViewModel.IpInfoData>) {
        if (!::packetData.isInitialized) {
            return
        }
        val highRiskCountries =
            listOf("Russia", "North Korea", "Ukraine", "China", "Nigeria", "Romania", "India")

        // Group packets by app package name
        val packetsByApp = packetData
            .filter { it.applicationInfo != null && it.applicationInfo.packageName != null }
            .groupBy { it.applicationInfo.packageName!! }

        for ((packageName, packets) in packetsByApp) {

            for (packet in packets) {
                val ip = packet.destinationIP.removePrefix("/")
                val country = updatedMap[ip]?.country ?: continue
                if (country in highRiskCountries) {
                    appWarnings[packageName]?.let { warnings ->
                        val updatedWarnings = warnings.toMutableList()
                        updatedWarnings[updatedWarnings.lastIndex] = true
                        appWarnings[packageName] = updatedWarnings
                    }
                }
            }
        }
    }



    fun onListItemClick(view: View) {
        val ipTextView = view.findViewById<TextView>(R.id.ipTextView)
        val ipAddress = ipTextView.text.toString()

        val link = "https://whatismyipaddress.com/ip/$ipAddress"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        startActivity(intent)
    }

}