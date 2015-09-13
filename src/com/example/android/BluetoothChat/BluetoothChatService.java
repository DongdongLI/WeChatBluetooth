
package com.example.android.BluetoothChat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


public class BluetoothChatService {
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";

    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    // three threads obj
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    // indicates the state of client and host
    private int mState;

    public static final int STATE_NONE = 0;       // initial
    public static final int STATE_LISTEN = 1;     // listen for incoming
    public static final int STATE_CONNECTING = 2; // connect to server
    public static final int STATE_CONNECTED = 3;  // connected with someone

    
    public BluetoothChatService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE; // initial the state
        mHandler = handler; // get the handler
    }

    // change the state as required
    private synchronized void setState(int state) {
        if (D) Log.d("demo1", "setState() " + mState + " -> " + state);
        mState = state;

        //update UI as needed
        mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {
    	// when start, all thread should stop, and there should be a brand new accept thread start waiting
        if (D) Log.d("demo1", "start");

        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }

    
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d("demo1", "connect to: " + device);

        // kill existing connect thread
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // cut existing connections
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // try to connect the chosen devie
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

   
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d("demo1", "connected");
        // since connected, canceling all unnecessary threads
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // send device name back to UI
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    
    public synchronized void stop() {
        if (D) Log.d("demo1", "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        setState(STATE_NONE);
    }

    public void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) {
        	// check state
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    
    private void connectionFailed() {
    	//clean up the mess after failure
        setState(STATE_LISTEN);
        // ask UI to make a toast
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Unable to connect device"); // the toast string
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    
    private void connectionLost() {
        setState(STATE_LISTEN);
        Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    
    private class AcceptThread extends Thread {
        // this thread will start the "ServerSocket"
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.d("demo1", "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d("demo1", "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            BluetoothSocket socket = null;

            // check the state
            while (mState != STATE_CONNECTED) {
                try {
                    // try to accept forever until connected
                	socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.d("demo1", "accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothChatService.this) {
                        switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // right way
                            connected(socket, socket.getRemoteDevice());
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // something is wrong, restart
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.d("demo1", "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d("demo1", "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.d("demo1", "close() of server failed", e);
            }
        }
    }


    
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.d("demo1", "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            //stop discovery if needed
            mAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {
                // use this function to clean up the mess
            	connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.d("demo1", "unable to close() socket during connection failure", e2);
                }
                // restart the service
                BluetoothChatService.this.start();
                return;
            }

            synchronized (BluetoothChatService.this) {
                mConnectThread = null;
            }

            // connected
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.d("demo1", "close() of connect socket failed", e);
            }
        }
    }

    
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        // for read and write
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d("demo1", "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.d("demo1", "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    // keep reading 
                	bytes = mmInStream.read(buffer);
                    mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                    // show the message on UI
                } catch (IOException e) {
                    Log.d("demo1", "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }


        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
                
            } catch (IOException e) {
                Log.d("demo1", "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.d("demo1", "close() of connect socket failed", e);
            }
        }
    }
}
