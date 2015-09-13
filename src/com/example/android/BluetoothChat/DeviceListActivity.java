package com.example.android.BluetoothChat;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;


public class DeviceListActivity extends Activity {
    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;

    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private BluetoothAdapter bluetoothAdapter;
    // one for paired devices, one for new devices
    private ArrayAdapter<String> pairedDevAdapter;
    private ArrayAdapter<String> newDevAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);

        // Set result CANCELED incase the user backs out
        setResult(Activity.RESULT_CANCELED);

        // scan button
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });
        // two adapters for each listview
        pairedDevAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        newDevAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(pairedDevAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(newDevAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // register for events "Something is found"
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // register for events "Search is done"
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                pairedDevAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
        	// add one line in listview indicates there is no device
            String noDevices = getResources().getText(R.string.none_paired).toString();
            pairedDevAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (bluetoothAdapter != null) { //stop searching
            bluetoothAdapter.cancelDiscovery();
        }

        // unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }

   
    private void doDiscovery() {
        if (D) Log.d("demo1", "doDiscovery()");

        // progress bar in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // title new devices
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
    }

    // click listener
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // stop searching
            bluetoothAdapter.cancelDiscovery();

            //mac
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // put address in result
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            // send result back to mainActivity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // only add when it is new
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    newDevAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                // nothing here, add "nothing " to the list
                if (newDevAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    newDevAdapter.add(noDevices);
                }
            }
        }
    };

}
