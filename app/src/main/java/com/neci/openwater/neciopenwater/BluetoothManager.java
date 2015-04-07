package com.neci.openwater.neciopenwater;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

/**
 * This is the communication class for communicating with the open source photometer developed by NECi for
 * scientific purposes only. This is subject to the Apache 2.0 license and the GNU General Public License.
 *
 * Please make sure you have the following permissions in your AndroidManifest.xml file:
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 *
 * This class uses standard message handling as provided by android.os.Handler class, please refer to
 * Android documention on how to use the class to get the message received by the photometer.
 *
 * Before getting a reading a Handler must be attached to this class in order to properly get the data.
 *
 * Standard procedure to use this class properly are as follows:
 * 		startUp()
 *		Connect()
 *		setHandler()
 *		getReading()
 *		closeBTSockets()
 */
public class BluetoothManager {

    private static final String TAG = "BluetoothManager";
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;
    private static String address = "XX:XX:XX:XX:XX:XX";
    private static final UUID MY_UUID = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");
    private InputStream inStream = null;
    Handler handler;
    byte delimiter = 10;
    boolean stopWorker = false;
    int readBufferPosition = 0;
    byte[] readBuffer = new byte[1024];

    Context appContext;

    private boolean flag = false;
    private boolean connected = false;

    private static boolean running = false;

    public static final String KEY = "key";

    public void setHandler(Handler handler)
    {
        this.handler = handler;
    }

    public boolean isConnected()
    {
        return connected;
    }

    public BluetoothManager(Context context) {
        appContext = context;
    }

    public boolean startUp(Activity callingActivity)
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) { // Device does not support Bluetooth
            Log.e("Bluetooth ","not found");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            callingActivity.startActivityForResult(enableBtIntent, 1);
            Toast.makeText(appContext, "Please Enable Bluetooth", Toast.LENGTH_LONG).show();
            return false;
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                Log.d(TAG,device.getName());
                if (name.contains("NECi")) {
                    address = device.getAddress();
                    break;
                }
            }
        }

        return true;
    }

    public Set<String> getAvailableDeviceList() {
        HashSet<String> result = new HashSet<String>();
        if (mBluetoothAdapter == null) return result;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // Loop through paired devices
        for (BluetoothDevice device_l : pairedDevices) {
            String name = device_l.getName();
            //All NECi photometers have the naming convention "NECi-####"
            if (name.matches("^NECi-.*$")) {
                result.add(name);
            }
        }
        return result;
    }

    public void getReading(String dataToSend) {

        if(!connected)
        {
            try {
                Connect();
                Log.d(TAG, "Bluetooth connection established");
            } catch (Exception e) {
                Log.d(TAG, e.getStackTrace()[0].toString());
                Log.d(TAG, "Bluetooth connection failed");
            }
        }
        else
            beginListenForData();

        if(!flag)
        {
            try {
                writeData(dataToSend);
                Log.d(TAG, "Attempted to send/receive");
            } catch (Exception e) {
                Log.d(TAG, "Failed to send/receive");
            }
        }
        else
        {
            Toast.makeText(appContext, "Bluetooth Connection Failed, try again", Toast.LENGTH_LONG).show();
            connected = false;
            flag = false;

            closeBTSockets();
            Message msg = handler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putString(KEY, "Error");
            msg.setData(bundle);
            handler.sendMessage(msg);
            stopWorker = true;
        }

    }

    public void Connect() {
        Log.d(TAG, address);
        BluetoothDevice device = null;
        if(!address.equals("XX:XX:XX:XX:XX:XX"))
        {
            device = mBluetoothAdapter.getRemoteDevice(address);
        }
        else
        {
            Toast.makeText(appContext, "No device found", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Connecting to ... " + device);
        mBluetoothAdapter.cancelDiscovery();
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            btSocket.connect();
            connected = true;
            Log.d(TAG, "Connection made.");
        } catch (IOException e) {
            try {
                flag = true;
                btSocket.close();
            } catch (Exception e2) {
                Log.d(TAG, "Unable to end the connection");
            }
            Log.d(TAG, "Socket creation failed");
            Log.d(TAG, e + "");

            Toast.makeText(appContext, "Bluetooth Connection Failed, try again", Toast.LENGTH_LONG).show();
            return;
        }

        beginListenForData();
    }

    private void writeData(String data) {
        try {
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            Log.d(TAG, "Bug BEFORE Sending stuff", e);
        }

        String message = data;
        byte[] msgBuffer = message.getBytes();

        Log.d("DAS", msgBuffer.length +"");

        try {
            outStream.write(msgBuffer);
        } catch (IOException e) {
            Log.d(TAG, "Bug while sending stuff", e);
        }
    }

    public void closeBTSockets() {

        connected = false;

        try {
            btSocket.close();
            Log.d(TAG, "BT Socket Closed");
        } catch (Exception e) {
            Log.d(TAG, "BT Socket error", e);
        }
    }

    private void beginListenForData()   {
        stopWorker = false;
        if(!running) {
            try {
                inStream = btSocket.getInputStream();
            } catch (IOException e) {}

            running = true;
            Thread workerThread = new Thread(new Runnable() {
                public void run() {
                    byte[] packetBytes = new byte[32];
                    int numBytes = 0;

                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                        try {
                            numBytes = inStream.read(packetBytes);

                            if (numBytes < 1)
                                try {
                                    Thread.sleep((long) 1);
                                } catch (InterruptedException e) {}

                            System.arraycopy(packetBytes, 0, readBuffer, readBufferPosition, numBytes);
                            readBufferPosition += numBytes;

                            String data = new String(readBuffer, "US-ASCII");
                            if (data.contains("\r\n")) {
                                Message msg = handler.obtainMessage();
                                Bundle bundle = new Bundle();
                                bundle.putString(KEY, data.trim());
                                msg.setData(bundle);
                                handler.sendMessage(msg);
                                stopWorker = true;
                                running = false;
                                readBufferPosition = 0;
                                Arrays.fill(readBuffer, (byte) 0);
                            }

                        } catch (IOException ex) {
                            stopWorker = true;
                            running = false;
                        }
                    }
                }
            });

            workerThread.start();
        }
    }
}
