package com.jhp.electricskateboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

/**
 * Created by J Park on 30/01/2017.
 */
public class ActivityOverlayWithBluetoothMonitoring extends MainActivity {

    // The BroadcastReceiver that tracks network connectivity changes.
    private BluetoothConnectionReceiver receiver;

    @Override
    public void onResume() {
        super.onResume();
        // Check the initial bluetooth connection and also monitor changes in the
        // bluetooth connection during the use of the app
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);

        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        receiver = new BluetoothConnectionReceiver();
        registerReceiver(receiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    /**
     * Call the handleUnconnectedBTSocket() method from the parent ElectricSkateboardActivity.java class
     */
    public void handleUnconnectedBTSocket() {
        super.handleUnconnectedBTSocket();
    }

    /**
     * This BroadcastReceiver intercepts the BluetoothDevice.ACTION_ACL_CONNECTED and
     * BluetoothDevice.ACTION_ACL_DISCONNECTED, which indicates a bluetooth connection change.
     */
    public class BluetoothConnectionReceiver extends BroadcastReceiver {
        public BluetoothConnectionReceiver(){
            //No initialisation code needed
        }

        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();

            if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                Toast msg = Toast.makeText(getBaseContext(), "Bluetooth device connected", Toast.LENGTH_SHORT);
                msg.show();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                // Note it takes ~20 seconds for a disconnection to be detected by Android.
                // (However, there is an instant detection if you directly turn off bluetooth on your phone)
                handleUnconnectedBTSocket();
            }
        }
    }
}