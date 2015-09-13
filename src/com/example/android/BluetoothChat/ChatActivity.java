
package com.example.android.BluetoothChat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class ChatActivity extends Activity {
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // key for handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView titleText;
    private ListView conversationListView;
    private EditText typeBox;
    private Button sendBtn;

    private String connectedDevName = null;
    private ArrayAdapter<String> conversationAdapter; // containing all the conversation
    private StringBuffer messageBuf;
    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothChatService service = null; // the object of the bluetooth service


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.d("demo1", "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        titleText = (TextView) findViewById(R.id.title_left_text);
        titleText.setText(R.string.app_name);
        titleText = (TextView) findViewById(R.id.title_right_text);

        // Get local Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // check if the devices supports bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.d("demo1", "++ ON START ++");

        // turn bluetooth if necessary
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (service == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.d("demo1", "+ ON RESUME +");
        // need to check if the service is empty
        if (service != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (service.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              service.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // array adapter containing all messages
        conversationAdapter = new ArrayAdapter<String>(this, R.layout.message);
        conversationListView = (ListView) findViewById(R.id.in);
        conversationListView.setAdapter(conversationAdapter);

        // user input
        typeBox = (EditText) findViewById(R.id.edit_text_out);
        typeBox.setOnEditorActionListener(mWriteListener);

        // send button
        sendBtn = (Button) findViewById(R.id.button_send);
        sendBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // get message from editTextView
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // init service obj with handler obj for call back
        service = new BluetoothChatService(this, mHandler);

        //empty the buffer
        messageBuf = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.d("demo1", "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.d("demo1", "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // stop  service
        if (service != null) service.stop();
        if(D) Log.d("demo1", "--- ON DESTROY ---");
    }
    // make sure the device can be discovered
    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (bluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 200);
            startActivity(discoverableIntent);
        }
    }

    
    private void sendMessage(String message) {
        // only send message when connected
        if (service.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        
        if (message.trim().length() > 0) {
            byte[] send = message.getBytes();
            service.write(send);

            // clean up buffer
            messageBuf.setLength(0);
            typeBox.setText(messageBuf);
        }
    }
    // waiting for user to hit return after typing
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            // state changing
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                // change to connected
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    titleText.setText(R.string.title_connected_to);
                    titleText.append(connectedDevName);
                    conversationAdapter.clear();
                    break;
                // try to get connected
                case BluetoothChatService.STATE_CONNECTING:
                    titleText.setText(R.string.title_connecting);
                    break;
                // wait to be connect
                case BluetoothChatService.STATE_LISTEN:
                // the original status
                case BluetoothChatService.STATE_NONE:
                    titleText.setText(R.string.title_not_connected);
                    break;
                }
                break;
            // there's conversation going out
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // first show it in local window
                String writeMessage = new String(writeBuf);
                conversationAdapter.add("Me:  " + writeMessage);
                break;
            // incoming message
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                String readMessage = new String(readBuf, 0, msg.arg1);
                conversationAdapter.add(connectedDevName+":  " + readMessage);
                break;
            // send back device info after connected
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                connectedDevName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + connectedDevName, Toast.LENGTH_SHORT).show();
                break;
            // this message indicates that the activity should make a toast for the servie
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
    // work with startActivityForResult
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            if (resultCode == Activity.RESULT_OK) {
                // mac address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                service.connect(device);
            }
            break;
        // the prompt of turning on bluetooth
        case REQUEST_ENABLE_BT:
            if (resultCode == Activity.RESULT_OK) {
               setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    // the menu items
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.scan:
            // looking for devices nearby
        	Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.discoverable:
            // be discovered (to be host)
            ensureDiscoverable();
            return true;
        }
        return false;
    }

}