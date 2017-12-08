package com.example.angelo.babymonitor;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.net.SocketException;


public class MainActivity extends AppCompatActivity implements  View.OnClickListener{

    Button sensorButton = null;
    Button listenerButton = null;

    public MainActivity() throws SocketException {
    }

    /*
    * #############################################################################################
    *                           ACTIVITY LIFECYClE
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorButton = (Button) findViewById(R.id.sensorButton);
        sensorButton.setOnClickListener(this);
        listenerButton = (Button) findViewById(R.id.listenerButton);
        listenerButton.setOnClickListener(this);
    }

    /*
    * Enable the broadcast receiver and showing Views...
    * */
    @Override
    public void onResume() {
        super.onResume();
    }

    /*
    * Disabling the broadcast receiver
    * */
    @Override
    public void onPause() {
        super.onPause();
    }


    /*
    * Disconnetting the WifiP2p Connections (deallocating the group and deleting it!)
    * */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /*
    * #############################################################################################
    *                           HANDLE USER CLICKS on  BUTTONS
    * */
    /*
    * Handle the USER ACTIONS --> listener and sensor button to initialize the DEVICE BEHAVIOUR
    * */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onClick(View view) {

        if(view.getId() == R.id.sensorButton){

            startActivity(new Intent(this, SensorActivity.class));
        }

        if(view.getId() == R.id.listenerButton){

            startActivity(new Intent(this, ListenerActivity.class));
        }

    }

}
