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
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
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

/**
 * Represents an application entry with a selectable checkbox state.
 * Used to handle the selection process of apps before monitoring starts.
 *
 * @property applicationInfo Metadata about the installed application, such as package name,
 * icon, and other system-level details provided by the Android system.
 * @property isChecked Indicates whether the app is selected for monitoring.
 * Defaults to `false`.
 */
data class AppInfoCheckbox(
    val applicationInfo: ApplicationInfo,
    var isChecked: Boolean = false
)

class MainActivity : AppCompatActivity() {

    /** View binding for activity_main.xml layout */
    private lateinit var binding: ActivityMainBinding

    /** Request code used to identify VPN permission result */
    private val VPN_REQUEST_CODE: Int = 0x0F

    /** Tracks whether the app is waiting for the VPN to start */
    private var waitingForVPNStart: Boolean = false

    /** Holds the VPN service launch intent, or null if not started */
    private var vpnIntent: Intent? = null

    /** Reference to the running VPN service instance, or null if not bound */
    private var vpnService: LocalVPNService? = null

    /** Indicates whether the VPN service is currently bound */
    private var vpnServiceBound: Boolean = false

    /**
     * Broadcast receiver that listens for VPN state updates.
     * Updates `waitingForVPNStart` flag based on whether the VPN is running.
     */
    private val vpnStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (LocalVPNService.BROADCAST_VPN_STATE == intent.action) {
                if (intent.getBooleanExtra("running", false)) waitingForVPNStart = false
            }
        }
    }
    /**
     * Handles service binding and unbinding for the VPN service.
     * Stores the reference to the running VPN service upon connection.
     */
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

    /** Stats view-model reference */
    private lateinit var statsViewModel: StatsViewModel

    /** Packet data ready for analysis. VPN service saves outgoing packets into this array */
    lateinit var packetData: ArrayList<LocalVPNService.PacketInfo>

    /** List of warning indicators for individual apps
    * <<packageName>, <warning_flags>>
    */
    var appWarnings: MutableMap<String, List<Boolean>> = mutableMapOf()

    /** List of all installed applications */
    val allApps = mutableListOf<AppInfoCheckbox>()

    /**
     * Initializes the activity, sets up view binding, configures the UI, and prepares the bottom navigation bar.
     * Also fetches and displays a list of installed apps, initializes the stats ViewModel, and registers for VPN state updates.
     *
     * @param savedInstanceState The previously saved instance state, if available.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the view
        binding = ActivityMainBinding.inflate(layoutInflater)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(binding.root)

        // Set up bottom navigation
        val navView: BottomNavigationView = binding.navView
        val listView: ListView = findViewById(R.id.applist)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_risks,
                R.id.navigation_stats,
                R.id.navigation_traffic
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        //Disable navigations until analysis runs
        for (i in 0 until navView.menu.size()) {
            navView.menu.getItem(i).isEnabled = false
            val disabledColor = Color.parseColor("#A0A0A0")
            navView.itemIconTintList = ColorStateList.valueOf(disabledColor)
            navView.itemTextColor = ColorStateList.valueOf(disabledColor)
        }

        // Fetch and fill the applist with installed apps
        getallapps()

        val adapter = AppListAdapter(this, allApps, packageManager)

        listView.adapter = adapter

        // Initialize the checkbox
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
            // Process the IP-country data when it finishes fetching
            onIpCountryMapUpdated(updatedMap)
        }
    }

    /**
     * Fetches all installed apps from the packageManager. Fills the `allApps`
     */
    fun getallapps() {
        val infos =
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter { info ->
                info.category != ApplicationInfo.CATEGORY_UNDEFINED
            }

        for (info in infos) {
            allApps.add(AppInfoCheckbox(info, false))
        }
    }

    /**
     * Prepares and starts the VPN service. Requests VPN permission if needed, then proceeds to bind the service.
     */
    private fun startVPN() {
        val localVpnIntent = VpnService.prepare(this)
        if (localVpnIntent != null) startActivityForResult(
            localVpnIntent,
            VPN_REQUEST_CODE
        ) else onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
    }

    /**
     * Handles the result of the VPN permission request. If granted, starts the VPN service and binds it.
     *
     * @param requestCode The request code passed in `startActivityForResult` (VPN_REQUEST_CODE)
     * @param resultCode The result code returned by the activity (RESULT_OK)
     * @param data Any additional data returned by the activity (in this instance always null)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            waitingForVPNStart = true
            val packageNameList: ArrayList<String> = ArrayList()
            for (app in allApps) {
                if (app.isChecked) {
                    packageNameList.add(app.applicationInfo.packageName)
                }
            }
            val intent = Intent(this, LocalVPNService::class.java)
            vpnIntent = intent
            // Saves the selected applications to intent
            intent.putStringArrayListExtra("allowedApplications", packageNameList)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
            startService(intent)
        }
    }

    /**
     * Starts or stops the VPN service. Upon stopping analysis is automatically triggered.
     */
    fun toggleScan(view: View) {
        if (vpnIntent == null) {   // START
            ViewCompat.setBackgroundTintList(
                view,
                ColorStateList.valueOf(Color.parseColor("#FE2727"))
            )
            view.rootView.findViewById<TextView>(R.id.textView).text = "Stop scan"
            startVPN()
            LocalVPNService.setRunning(true)
        } else {   // STOP
            val activity = view.context as Activity
            val textView = activity.findViewById<TextView>(R.id.textView)
            val checkBox = activity.findViewById<CheckBox>(R.id.selectAllCheckbox)
            val appList = activity.findViewById<ListView>(R.id.applist)

            textView.text = "Scan complete"
            ViewCompat.setBackgroundTintList(
                view,
                ColorStateList.valueOf(Color.parseColor("#27FEB3"))
            )

            // Re-enable navigation
            val navView: BottomNavigationView = binding.navView

            for (i in 1 until navView.menu.size()) {
                val item = navView.menu.getItem(i)
                item.isEnabled = true
            }
            val enabledColor = Color.parseColor("#4a4a4a")
            navView.itemIconTintList = ColorStateList.valueOf(enabledColor)
            navView.itemTextColor = ColorStateList.valueOf(enabledColor)

            // Stop VPN service
            var ret = vpnService?.stopVPNService()
            if (ret != null) {
                packetData = ret
            }
            unbindService(connection)
            vpnServiceBound = false
            vpnIntent = null
            analyzeData()
        }

    }


    /**
     * Analyses captured packet data.
     */
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

        val topApp = sortedAppsByTraffic.getOrNull(0)
        val secondApp = sortedAppsByTraffic.getOrNull(1)

        // Set warning flags for each app
        for ((packageName, packets) in packetsByApp) {
            var encryptionWarning = false
            var protocolWarning = false
            // Country warning flag needs to be set only after we get back the geo-location data.
            var countryWarning = false
            val trafficWarning =
                topApp != null && secondApp != null && topApp.value >= 3 * secondApp.value && topApp.key == packageName

            val total = packets.size
            val unencryptedProtocols = setOf(
                "SMTP(25)",
                "HTTP(80)",
                "DNS(53)",
                "XMPP/Jabber(5222)",
                "MQTT(1883)",
                "HTTP-Alt(8080)",
                "SSDP(1900)",
                "mDNS(5353)",
                "NTP(123)"
            )

            val unencryptedCount = packets.count { it.protocol in unencryptedProtocols }
            val otherProtocolCount = packets.count { it.protocol == "Other" }

            val unencryptedRatio = unencryptedCount.toDouble() / total
            val otherProtocolRatio = otherProtocolCount.toDouble() / total

            if (unencryptedRatio > 0.5) {
                encryptionWarning = true
            }

            if (otherProtocolRatio > 0.5) {
                protocolWarning = true
            }

            appWarnings[packageName] =
                mutableListOf(encryptionWarning, protocolWarning, countryWarning, trafficWarning)
        }

        statsViewModel.updatePieChart(packetData)
        statsViewModel.updateIPList(packetData)

    }

    /**
     * Sets the country warning flag based on the destination country
     */
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
                    // Update the warnings
                    appWarnings[packageName]?.let { warnings ->
                        val updatedWarnings = warnings.toMutableList()
                        updatedWarnings[updatedWarnings.lastIndex] = true
                        appWarnings[packageName] = updatedWarnings
                    }
                }
            }
        }
    }

    /**
     * Handles redirecting to IP information website in Stats Screen
     */
    fun onListItemClick(view: View) {
        val ipTextView = view.findViewById<TextView>(R.id.ipTextView)
        val ipAddress = ipTextView.text.toString()

        val link = "https://whatismyipaddress.com/ip/$ipAddress"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        startActivity(intent)
    }

}