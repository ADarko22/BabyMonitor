package com.example.angelo.babymonitor;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ListenerActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, View.OnClickListener, WifiP2pManager.ConnectionInfoListener, WifiP2pManager.ChannelListener{

    private final static  String TAG = "ANGELO_APPLICATION";

    /*
    * UI elements
    * */
    Button disconnectButton = null;
    Button disconnectDevicesButton = null;
    Button discoverServiceButton = null;

    Button startClientButton = null;
    Button startReceiveStreamButton = null;
    Button stopReceiveStreamButton = null;

    TextView myDeviceName = null;
    TextView myDeviceAddress = null;
    TextView myDeviceStatus = null;

    ListView discoveredDeviceList = null;
    private DeviceListAdapter discoveredDeviceListAdapter = null ;

    /*WiFi Direct components*/
    private final IntentFilter intentFilter = new IntentFilter();
    public BroadcastReceiver receiver = null;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    WifiP2pDevice myDevice;

    private List<WifiP2pDevice> discoveredPeers = new ArrayList<WifiP2pDevice>();

    private WifiP2pInfo info;

    boolean isWifiP2pEnabled = false;

    /*Discovery Service*/
    final HashMap<String, String> buddies = new HashMap<String, String>();
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE = "_noiseservice";

    public static final String SERVICE_REG_TYPE = "_presence._tcp";
    private WifiP2pDnsSdServiceRequest serviceRequest;


    /*control flat for listener service status*/
    private boolean listenerServiceActive = false;
    private boolean firstConnection = true;

    public static final String DISCONNECT_NOTIFY = "DISCONNECT_NOTIFY";
    public static final String CONNECT_NOTIFY = "CONNECT_NOTIFY";

    private Intent intent = null;

    /*BroadCast Receiver to listen LitenerService notifications*/
    private BroadcastReceiver connectionNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent != null){
                String action = intent.getAction();
                Log.d(TAG, "connectionNotificationReceiver: "+action);
                if(DISCONNECT_NOTIFY.equals(action)){
                    /*Handling the UI reset when Socket Communication is disconnected*/
                    startReceiveStreamButton.setVisibility(View.GONE);
                    stopReceiveStreamButton.setVisibility(View.GONE);

                    disconnectButton.setEnabled(true);
                    disconnectDevicesButton.setVisibility(View.GONE);

                    startClientButton.setVisibility(View.VISIBLE);

                    listenerServiceActive = false;
                }
                if(CONNECT_NOTIFY.equals(action)){
                    /*Handling the UI reset when Socket Communication is connected*/
                    startReceiveStreamButton.setVisibility(View.VISIBLE);
                    stopReceiveStreamButton.setVisibility(View.VISIBLE);;
                    startReceiveStreamButton.setEnabled(false);

                    disconnectButton.setEnabled(false);
                    disconnectDevicesButton.setVisibility(View.VISIBLE);

                    startClientButton.setVisibility(View.GONE);

                    listenerServiceActive = true;
                }
            }
        }
    };

    ProgressDialog progressDialog = null;

    /*
    * #############################################################################################
    *                           ACTIVITY LIFECYClE
    * */

    /*
    * Initializating all Views, adapters, the intent filter for a dynamic broadcast receiver
    * and WifiP2p Manager & Channel
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listener);

        discoverServiceButton = (Button) findViewById(R.id.discoverServicesButton);
        discoverServiceButton.setOnClickListener(this);

        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(this);

        disconnectDevicesButton = (Button)findViewById(R.id.disconnectDevicesButton);
        disconnectDevicesButton.setOnClickListener(this);

        myDeviceName = (TextView) findViewById(R.id.myDeviceName);
        myDeviceAddress = (TextView) findViewById(R.id.myDeviceAddress);
        myDeviceStatus = (TextView) findViewById(R.id.myDeviceStatus);

        discoveredDeviceList = (ListView) findViewById(R.id.discoveredDeviceList);
        discoveredDeviceListAdapter = new DeviceListAdapter(ListenerActivity.this, discoveredPeers);
        discoveredDeviceList.setAdapter(discoveredDeviceListAdapter);
        discoveredDeviceList.setOnItemClickListener(this);

        startClientButton = (Button) findViewById(R.id.startClient);
        startClientButton.setOnClickListener(this);
        startReceiveStreamButton = (Button) findViewById(R.id.startReceiveStream);
        startReceiveStreamButton.setOnClickListener(this);
        stopReceiveStreamButton = (Button) findViewById(R.id.stopReceiveStream);
        stopReceiveStreamButton.setOnClickListener(this);

        //Change in Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        //Change in Wi-Fi P2P connectivity.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        //Change in device details.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        discoverServiceButton.setVisibility(View.GONE);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        /*DELETING ALL EXISTENT WIFI P2P GROUPS to ensure a good group creation!*/
        deletePersistentGroup(manager, channel);
    }

    /*
    * Enable the broadcast receiver and showing Views...
    * */

    @Override
    protected void onStart() {
        super.onStart();

        startServiceRegistration();
    }

    @Override
    public void onResume() {
        super.onResume();

        /* Enabling the receiver if Device is not Connected*/
        receiver = new WifiP2pBroadcastReceiver(manager, channel, ListenerActivity.this);
        registerReceiver(this.receiver, intentFilter);

        /*Enabling the Local Broadcast Receiver for Listener Service Connection Notification*/
        IntentFilter disconnectIntentFilter = new IntentFilter();
        disconnectIntentFilter.addAction(ListenerService.DISCONNECT_NOTIFY);
        disconnectIntentFilter.addAction(ListenerService.CONNECT_NOTIFY);
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionNotificationReceiver, disconnectIntentFilter);

        showMyDevice();

        if(myDevice != null && myDevice.status != WifiP2pDevice.CONNECTED)
            discoverServiceButton.setVisibility(View.VISIBLE);
    }

    /*
    * Disabling the broadcast receiver
    * */
    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionNotificationReceiver);
    }

    /*
    * Disconnetting the WifiP2p Connections (deallocating the group and deleting it!)
    * */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if( myDevice != null)
            disconnect();

        Log.d(TAG, "onDestroy(): Stop ListenerService!");
        intent = new Intent(this, ListenerService.class);
        stopService(intent);
    }

    /*
    * ############################################################################################
    * *                                 WIFI DIRECT SERVICE DISOVERY
    * */

    /*
    * Registrating the service: creating a service with its informations in a record and adding it!
    * */
    private void startServiceRegistration() {

        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "startServiceRegistration() SUCCESS!");
                //Toast.makeText(ListenerActivity.this, "Added Local Service!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(int error) {
                Log.d(TAG, "startServiceRegistration() ERROR!"+ error);
                Toast.makeText(ListenerActivity.this, "Error Adding Local Service!", Toast.LENGTH_SHORT).show();
            }
        });

    }

    /*
    * Setting a listener to reply a DNSServiceRequest --> implementing the CallBack on ServiceAvailable: adding the discovered service (device!)
    * Creating a ServiceRequest & adding it to the channel! & call the Service Discovery with the request created!
    * */
    private void discoverService() {

        progressDialog = ProgressDialog.show(this,"Press back to cancel","Finding Peers...", true, true, new DialogInterface.OnCancelListener(){
            @Override
            public void onCancel(DialogInterface dialogInterface) {
            }
        });

        /*
        * Adding the service discovered --> the Sensor Device  to the list of Discovered  peers to allow the connection with its service...
        * */
        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {

                        // A service has been discovered
                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {

                            if (progressDialog != null && progressDialog.isShowing())
                                progressDialog.dismiss();

                            /*handling a pending connection...*/
                            if(srcDevice.status == WifiP2pDevice.INVITED) {
                                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "discoverService(): cancelConnect() SUCCESS!");
                                    }
                                    @Override
                                    public void onFailure(int error) {
                                        Log.d(TAG, "discoverService(): cancelConnect() ERROR!"+ error);
                                    }
                                });
                            }
                            else if(srcDevice.isGroupOwner()) {
                                if (discoveredPeers.contains(srcDevice))
                                    discoveredPeers.remove(srcDevice);

                                discoveredPeers.add(srcDevice);
                            }
                            discoveredDeviceListAdapter.notifyDataSetChanged();
                        }
                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {
                    /*A new TXT record is available.*/
                    @Override
                    public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> record, WifiP2pDevice device) {
                        //Toast.makeText(ListenerActivity.this, "TXT Record Available!", Toast.LENGTH_SHORT).show();
                    }
                });

        /* After attaching listeners, create a service request and initiate discovery.*/
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "discoverService(): addServiceRequest() SUCCESS!");
                    }
                    @Override
                    public void onFailure(int error) {
                        Log.d(TAG, "discoverService(): addServiceRequest() ERROR!"+error);
                    }
                });
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "discoverService(): discoverServices() SUCCESS!");
            }
            @Override
            public void onFailure(int error) {
                /*Handling failure in Service Discovery!*/
                if (progressDialog != null && progressDialog.isShowing())
                    progressDialog.dismiss();
                Log.d(TAG, "discoverService(): discoverServices() ERROR!"+error);

                if (error == WifiP2pManager.NO_SERVICE_REQUESTS) {
                    /* initiate a stop on service discovery*/
                    manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "discoverService(): discoverServices(): stopPeerDiscovery() SUCCESS!");
                            /*initiate clearing of the all service requests*/
                            manager.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d(TAG, "discoverService(): discoverServices(): stopPeerDiscovery(): clearServiceRequestes SUCCESS!");
                                    // reset the service listeners, service requests, and discovery
                                    serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
                                    manager.addServiceRequest(channel, serviceRequest,
                                            new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    Log.d(TAG, "discoverService(): discoverServices(): stopPeerDiscovery(): clearServiceRequestes(): addServiceRequest() SUCCESS!");
                                                }
                                                @Override
                                                public void onFailure(int error) {
                                                    Log.d(TAG, "discoverService(): discoverServices(): stopPeerDiscovery(): clearServiceRequestes(): addServiceRequest() ERROR!"+error);
                                                }
                                            });
                                }
                                @Override
                                public void onFailure(int error) {
                                    Log.d(TAG, "discoverService(): discoverServices(): stopPeerDiscovery(): clearServiceRequestes() ERROR!"+error);
                                }
                            });
                        }
                        @Override
                        public void onFailure(int error) {
                            Log.d(TAG, "discoverService(): discoverServices(): stopPeerDiscovery(): ERROR!"+error);
                        }
                    });
                }
            }
        });
    }

    /*
    * #############################################################################################
    *                           WIFI DIRECT CONNECTION & GROUP MANAGEMENT
    * */

    /*
    * CallBack method triggered bye the requesteConnectionInfo on the Broadcast Receiver when the P2p connection status change!
    * Start the related service when the connection is estabilished!
    * */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo wifiP2pInfo) {

        info = wifiP2pInfo;

        if(info.groupFormed){
            //listener is never the group owner!
            if(firstConnection && !listenerServiceActive) {
                /*automatically start the listener service! (instead of using the button..)*/
                ListenerService.startActionStartConnection(this, info.groupOwnerAddress);
                firstConnection = false;
            }
        }
    }
    /*
    * Disconnecting the channel (by removing the group!)
    * & calling deletePersistentGroup to delete the static groups stored!
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void disconnect() {

        if(myDevice.status == WifiP2pDevice.CONNECTED)
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "disconnect(): removeGroup() SUCCESS!");
                }

                @Override
                public void onFailure(int error) {
                    Log.d(TAG, "disconnect(): removeGroup() ERROR!"+error);
                }
            });

        deletePersistentGroup(manager,channel);

        hideDeviceList();

        /*Showing the user interaction button according the behaviour chosen*/
        discoverServiceButton.setVisibility(View.VISIBLE);
        startClientButton.setVisibility(View.GONE);

        startReceiveStreamButton.setVisibility(View.GONE);
        stopReceiveStreamButton.setVisibility(View.GONE);

        /*Handling socket communication disconnections*/
        if(listenerServiceActive) {
            if (myDevice != null && myDevice.status == WifiP2pDevice.CONNECTED) {
                Log.d(TAG, "disconnect(): startActionDIsconnect()");
                ListenerService.startActionDisconnect(this);
            }
            else {
                Log.d(TAG, "disconnect(): startActionStopConnection()");
                ListenerService.startActionStopConnection(this);
            }
        }
    }

    /*
    * Deleting persistent groups!
    * (because estabilishing a WifiP2p connection leads the memoration of a persistent group, and all future connections
    * between the same devices, will restore that groups, with that choosen group owner...)
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static void deletePersistentGroup(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        try {
            Method method = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, int.class, WifiP2pManager.ActionListener.class);

            for (int netId = 0; netId < 32; netId++) {
                method.invoke(manager, channel, netId, null);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    * Callback method to inizialize the Manager when the channel is disconnected!
    * */
    @Override
    public void onChannelDisconnected() {
        if(manager != null){
            //Toast.makeText(ListenerActivity.this, "Channel Lost! Trying to restore ...", Toast.LENGTH_SHORT).show();

            manager.initialize(this, getMainLooper(), this);
        }
    }
    /*
    * #############################################################################################
    *                           HANDLE USER CLICKS on LISTVIEW and BUTTONS
    * */

    /*
    * Listener for the List View of DISCOVERED DEVICES --> show the CONNECT button when an item is clicked
    * and handle the visibility of this hidden button
    * */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

        /*Handling Connection to a Peer*/
        final Button connectButton = (Button)view.findViewById(R.id.connectButton);

        final int position = i;
        int connectButtonVisible;

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                WifiP2pConfig config = new WifiP2pConfig();

                config.deviceAddress = discoveredPeers.get(position).deviceAddress;
                config.wps.setup = WpsInfo.PBC;

                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectButton.setVisibility(View.GONE);
                    }
                    @Override
                    public void onFailure(int i) {
                        Toast.makeText(ListenerActivity.this, "Error Connecting to " + discoveredPeers.get(position).deviceAddress + "(" + i + ")", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        });

        if (discoveredPeers.get(position).status != WifiP2pDevice.AVAILABLE)
            connectButton.setVisibility(View.GONE);

        if (discoveredPeers.get(position).status == WifiP2pDevice.AVAILABLE) {
            connectButtonVisible = connectButton.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
            connectButton.setVisibility(connectButtonVisible);
        }

    }
    /*
    * Handle the USER ACTIONS
    *  #DICOVERY SERVICE in case of Listener Device
    *  and the DISCONNECT BUTTON to exit a group
    * */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onClick(View view) {

        if(view.getId() == R.id.discoverServicesButton) {

            discoverService();

            discoveredPeers.clear();
            showDeviceList();
        }

        if(view.getId() == R.id.disconnectButton){

            discoverServiceButton.setVisibility(View.VISIBLE);

            disconnect();
        }

        if(view.getId() == R.id.disconnectDevicesButton){
            /*the UI is updated by the Local BroadCast Receiver triggered by the ListenerService*/
            if(listenerServiceActive)
                ListenerService.startActionDisconnect(this);
        }

        if(view.getId() == R.id.startClient){

            /*start socket communication*/
            if(!listenerServiceActive)
                ListenerService.startActionStartConnection(this, info.groupOwnerAddress);
        }

        if(view.getId() == R.id.startReceiveStream){

            ListenerService.startActionStartStreaming(this);
            startReceiveStreamButton.setEnabled(false);
            stopReceiveStreamButton.setEnabled(true);
        }

        if(view.getId() == R.id.stopReceiveStream){

            ListenerService.startActionStopStreaming(this);
            startReceiveStreamButton.setEnabled(true);
            stopReceiveStreamButton.setEnabled(false);
        }
    }

    /*
    * #############################################################################################
    *                           MANAGE GUI ELEMETS
    * */

    /*
    *  Showing and Hiding the List Views
    * */
    public void showDeviceList() {

        findViewById(R.id.peersBar).setVisibility(View.VISIBLE);
        discoveredDeviceList.setVisibility(View.VISIBLE);
    }

    public void hideDeviceList(){
        findViewById(R.id.peersBar).setVisibility(View.GONE);

        discoveredDeviceList.setVisibility(View.GONE);
        discoveredPeers.clear();
        discoveredDeviceListAdapter.notifyDataSetChanged();
    }

    /*
    * Showing and Hiding the TextAreas with Info about my device
    * */
    public void showMyDevice(){

        findViewById(R.id.myDeviceBar).setVisibility(View.VISIBLE);

        myDeviceName.setVisibility(View.VISIBLE);
        myDeviceAddress.setVisibility(View.VISIBLE);
        myDeviceStatus.setVisibility(View.VISIBLE);

        if( myDevice!= null && myDevice.status == WifiP2pDevice.CONNECTED)
            disconnectButton.setVisibility(View.VISIBLE);
    }

    public void hideMyDevice(){

        findViewById(R.id.myDeviceBar).setVisibility(View.GONE);

        myDeviceName.setVisibility(View.GONE);
        myDeviceAddress.setVisibility(View.GONE);
        myDeviceStatus.setVisibility(View.GONE);
    }


    /*
    * Utility function to convert the WifiP2p Device Status from number to string
    * */
    public String getStatus(int statusCode){
        String status = null;
        if(statusCode == WifiP2pDevice.AVAILABLE)
            status = "Available";
        if(statusCode == WifiP2pDevice.CONNECTED)
            status = "Connected";
        if(statusCode == WifiP2pDevice.FAILED)
            status = "Failed";
        if(statusCode == WifiP2pDevice.INVITED)
            status = "Invited";
        if(statusCode == WifiP2pDevice.UNAVAILABLE)
            status = "Unavailable";
        return status;
    }

    /*
    * #############################################################################################
    *                       UPDATE INFO BY THE BROADCAST RECEIVER
    * */

    /*
    * Function called by the Broadcast Receiver when the infos about my device change..
    * */
    public void updateMyDevice(WifiP2pDevice device){
        myDevice = device;

        myDeviceName.setText(getString(R.string.my_device_name_textview) + myDevice.deviceName);
        myDeviceAddress.setText(getString(R.string.my_device_address_textview) + myDevice.deviceAddress);
        myDeviceStatus.setText(getString(R.string.my_device_status_textview) + getStatus(myDevice.status));

        if(myDevice != null) {
            if (myDevice.status == WifiP2pDevice.CONNECTED) {
                discoveredPeers.clear();
                discoveredDeviceListAdapter.notifyDataSetChanged();
                discoverServiceButton.setVisibility(View.GONE);
            }
            if (myDevice.status == WifiP2pDevice.AVAILABLE) {
                discoverServiceButton.setVisibility(View.VISIBLE);
                startClientButton.setVisibility(View.GONE);

                if(listenerServiceActive){
                    /*Handling a loss in WifiP2p Connection when the service is active!*/
                    Log.d(TAG,"updateMyDevice(): startActionStopConnectio()");
                    ListenerService.startActionStopConnection(this);
                }

                listenerServiceActive = false;
                firstConnection = true;
            }
        }
    }

    /*
    * Setting a boolean to know if the Wifi is enabled...
    * */
    public void setIsWifiP2pEnabled(boolean isEnabled){
        isWifiP2pEnabled = isEnabled;
    }

    public boolean isListenerServiceActive(){
        return listenerServiceActive;
    }
}

