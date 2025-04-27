/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.dp_project.hexene.localvpn;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.system.OsConstants;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.os.Binder;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dp_project.dp_packet_sniffer.R;

public class LocalVPNService extends VpnService
{
    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    public static final String BROADCAST_VPN_STATE = "xyz.hexene.localvpn.VPN_STATE";

    private ArrayList<PacketInfo> packetInfoMap = new ArrayList<>();

    private static boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface = null;

    private PendingIntent pendingIntent;

    private static LocalVPNService instance;

    private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;

    private Selector udpSelector;
    private Selector tcpSelector;

    public static class PacketInfo {
        public Date timestamp;
        public String protocol;
        public String destinationIP;
        public int payloadSize;

        public ApplicationInfo applicationInfo;

        public PacketInfo(Date timestamp, String protocol, String destinationIP, int payloadSize, ApplicationInfo applicationInfo) {
            this.timestamp = timestamp;
            this.protocol = protocol;
            this.destinationIP = destinationIP;
            this.payloadSize = payloadSize;
            this.applicationInfo = applicationInfo;
        }
    }


    public class LocalBinder extends Binder {
        public LocalVPNService getService() {
            return LocalVPNService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    @Override
    public void onCreate()
    {
        super.onCreate();
        instance = this;
        isRunning = true;

    }

    private void setupVPN(ArrayList<String> allowedApplications)
    {
        if (vpnInterface == null)
        {
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addRoute(VPN_ROUTE, 0);
            try {
            builder.addAllowedApplication(getPackageName());
            } catch(PackageManager.NameNotFoundException ignore)
            {
            }
            for (String allowedApplication : allowedApplications) {
                try {
                    builder.addAllowedApplication(allowedApplication);
                } catch(PackageManager.NameNotFoundException ignore)
                {
                }
            }
            vpnInterface = builder.setSession(getString(R.string.app_name)).setConfigureIntent(pendingIntent).establish();

            try
            {
                udpSelector = Selector.open();
                tcpSelector = Selector.open();
                deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
                deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
                networkToDeviceQueue = new ConcurrentLinkedQueue<>();

                executorService = Executors.newFixedThreadPool(5);
                executorService.submit(new UDPInput(networkToDeviceQueue, udpSelector));
                executorService.submit(new UDPOutput(deviceToNetworkUDPQueue, udpSelector, this));
                executorService.submit(new TCPInput(networkToDeviceQueue, tcpSelector));
                executorService.submit(new TCPOutput(deviceToNetworkTCPQueue, networkToDeviceQueue, tcpSelector, this));
                executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                        deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue));
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
                Log.i(TAG, "Started");
            }
            catch (IOException e)
            {
                // TODO: Here and elsewhere, we should explicitly notify the user of any errors
                // and suggest that they stop the service, since we can't do it ourselves
                Log.e(TAG, "Error starting service", e);
                cleanup();
            }
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ArrayList<String> allowedApplications = intent != null ? intent.getStringArrayListExtra("allowedApplications") : null;
        if (allowedApplications != null) {
            // Call setupVPN and pass the list of allowed applications
            setupVPN(allowedApplications);
        }
        return START_STICKY;
    }

    public static boolean isRunning()
    {
        return isRunning;
    }

    public static void setRunning(boolean value)
    {
        isRunning = value;
    }


    public static LocalVPNService getInstance()
    {
        return instance;
    }


    public ArrayList<PacketInfo> stopVPNService()
    {
        ArrayList<PacketInfo> temp = this.packetInfoMap;
        onDestroy();
        return temp;
    }

    @Override
    public void onDestroy()
    {
        isRunning = false;
        executorService.shutdownNow();
        cleanup();
        Log.i(TAG, "Stopped");
        super.onDestroy();

    }

    private void cleanup()
    {
        deviceToNetworkTCPQueue = null;
        deviceToNetworkUDPQueue = null;
        networkToDeviceQueue = null;
        ByteBufferPool.clear();
        closeResources(udpSelector, tcpSelector, vpnInterface);
    }

    // TODO: Move this to a "utils" class for reuse
    private static void closeResources(Closeable... resources)
    {
        for (Closeable resource : resources)
        {
            try
            {
                resource.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
    }

    private static class VPNRunnable implements Runnable
    {
        private static final String TAG = VPNRunnable.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor;

        private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
        private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;


        public VPNRunnable(FileDescriptor vpnFileDescriptor,
                           ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue,
                           ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue,
                           ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue)
        {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run()
        {
            Log.i(TAG, "Started");

            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();

            try
            {
                ByteBuffer bufferToNetwork = null;
                boolean dataSent = true;
                boolean dataReceived;
                String protocol = "null";
                while (!Thread.interrupted())
                {
//                    if(!isRunning)
//                    {
//                        Thread.sleep(10);
//                        continue;
//                    }
                    if (dataSent)
                        bufferToNetwork = ByteBufferPool.acquire();
                    else
                        bufferToNetwork.clear();

                    // TODO: Block when not connected
                    int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0) {
                        dataSent = true;
                        bufferToNetwork.flip();
                        Packet packet = new Packet(bufferToNetwork);

                        Log.d("Packet sent!", String.valueOf(packet.ip4Header.destinationAddress));

                        // TODO Save packet info
                        String appName = "Unknown";
                        String packageName = "Unknown";
                        String destIP = null;
                        ApplicationInfo appInfo = null;
                        if (packet.isTCP() || packet.isUDP()) {
                            destIP = packet.ip4Header.destinationAddress.toString();
                            int destPort = packet.isTCP() ? packet.tcpHeader.destinationPort : packet.udpHeader.destinationPort;

// Get UID of the connection owner
                            int uid = getConnectionOwnerUid(packet, destIP, destPort);

// Get app info from UID

                            if (uid != -1) {
                                PackageManager pm = getInstance().getPackageManager();
                                String[] packages = pm.getPackagesForUid(uid);
                                if (packages != null && packages.length > 0) {
                                    packageName = packages[0];
                                    try {
                                        appInfo = pm.getApplicationInfo(packageName, 0);
                                    } catch (PackageManager.NameNotFoundException ignored) {
                                    }
                                }
                            }

// Now create the PacketInfo

                        }
                        PacketInfo packetInfo = new PacketInfo(new Date(), protocol, destIP, packet.ip4Header.totalLength, appInfo);
                        if (packet.isUDP()) {
                            if (packet.udpHeader != null) {
                                packetInfo.protocol = getInstance().getProtocol(packet.udpHeader.destinationPort);
                                getInstance().packetInfoMap.add(packetInfo);
                            }
                            deviceToNetworkUDPQueue.offer(packet);
                        } else if (packet.isTCP()) {
                            if (packet.tcpHeader != null) {
                                packetInfo.protocol = getInstance().getProtocol(packet.tcpHeader.destinationPort);
                                getInstance().packetInfoMap.add(packetInfo);
                            }
                            deviceToNetworkTCPQueue.offer(packet);
                        } else {
                            Log.w(TAG, "Unknown packet type");
                            Log.w(TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }
                    }
                    else
                    {
                        dataSent = false;
                    }

                    ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    if (bufferFromNetwork != null)
                    {
                        bufferFromNetwork.flip();
                        while (bufferFromNetwork.hasRemaining())
                            vpnOutput.write(bufferFromNetwork);
                        dataReceived = true;

                        ByteBufferPool.release(bufferFromNetwork);

                    }
                    else
                    {
                        dataReceived = false;
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataSent && !dataReceived)
                        Thread.sleep(10);
                }
            }
            catch (InterruptedException e)
            {
                Log.i(TAG, "Stopping");
            }
            catch (Exception e)
            {
                Log.w(TAG, e.toString(), e);
            }
            finally
            {
//                closeResources(vpnInput, vpnOutput);
            }
        }

        private int getConnectionOwnerUid(Packet packet, String destIP, int destPort) {
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager == null) {
                    return -1;
                }

                int protocol = packet.isTCP() ? OsConstants.IPPROTO_TCP : OsConstants.IPPROTO_UDP;

                // Create local and remote socket addresses
                InetSocketAddress local = new InetSocketAddress(packet.ip4Header.sourceAddress.getHostAddress(), packet.isTCP() ? packet.tcpHeader.sourcePort : packet.udpHeader.sourcePort);
                InetSocketAddress remote = new InetSocketAddress(packet.ip4Header.destinationAddress.getHostAddress(), destPort);

                return connectivityManager.getConnectionOwnerUid(protocol, local, remote);
            } catch (Exception e) {
                Log.e(TAG, "getConnectionOwnerUid failed", e);
                return -1;
            }
        }


    }

    private String getProtocol(int port)
    {
        switch (port) {
            case 80:
                return "HTTP(80)";
            case 443:
                return "HTTPS(443)";
            case 25:
                return "SMTP(25)";
            case 53:
                return "DNS(53)";
            case 22:
                return "SSH(22)";
            case 1883:
                return "MQTT(1883)";
            default:
                return "Unknown";
        }
    }



}
