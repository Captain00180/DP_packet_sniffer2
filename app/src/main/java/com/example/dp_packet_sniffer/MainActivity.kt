package com.dp_packet_sniffer

import AppListAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.dp_packet_sniffer.ui.Stats.StatsViewModel
import com.example.dp_packet_sniffer.R
import com.example.dp_packet_sniffer.databinding.ActivityMainBinding
import com.example.hexene.localvpn.LocalVPNService
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
            // Cast the IBinder to LocalVPNService.LocalBinder
            val binder = service as LocalVPNService.LocalBinder
            // Get the LocalVPNService instance from the binder
            vpnService = binder.service
            vpnServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vpnServiceBound = false
        }
    }

    private lateinit var statsViewModel: StatsViewModel

    private lateinit var packetData: ArrayList<LocalVPNService.PacketInfo>

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
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

    }

    @RequiresApi(Build.VERSION_CODES.O)
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
        println("Button clicked!")
        if(vpnIntent == null)
        {
            startVPN()
            LocalVPNService.setRunning(true)
        } else
        {
            // Stop scan
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


    fun analyzeData()
    {
        statsViewModel.updatePieChart(packetData)
        statsViewModel.updateIPList(packetData)
    }


}