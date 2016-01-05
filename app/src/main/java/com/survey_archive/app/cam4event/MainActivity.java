package com.survey_archive.app.cam4event;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {

    private static final int REQUEST_ENABLE_BT = 22;
    private final Map<String, String> deviceMap = new HashMap();
    private ArrayAdapter<String> adapter;
    private final Map<String, BluetoothSocket> clientSockets = new HashMap<>();
    private final Map<String, BluetoothSocket> serverSockets = new HashMap<>();

    private List<String> cam4EventChannels = Arrays.asList(
            "38400000-8cf0-11bd-b23e-10b96e4ef00a",
            "38400000-8cf0-11bd-b23e-10b96e4ef00b",
            "38400000-8cf0-11bd-b23e-10b96e4ef00c");

    private Handler mHandler = null;

    private static class StringMessage {
        public final String sender;
        public final String contents;

        private StringMessage(String sender, String contents) {
            this.sender = sender;
            this.contents = contents;
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                deviceMap.put(device.getName(), device.getAddress());
                refreshDeviceList();

            }
        }
    };
    //String [] items = new String[20];

    private void refreshDeviceList() {
        adapter.clear();
        for (Map.Entry<String, String> next : deviceMap.entrySet()) {
            adapter.add(next.getKey());
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                // This is where you do your work in the UI thread.
                // Your worker tells you in the message what to do.
                StringMessage msg = (StringMessage)message.obj;

                Toast.makeText(getApplicationContext(),
                        "Msg from ["+ msg.sender +"]: " + msg.contents ,
                        Toast.LENGTH_LONG).show();
            }
        };
        setContentView(R.layout.activity_main);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);

        ListView listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(adapter);



        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth

        } else {
            Log.d("Cam4Event:", "bluetooth is here...");

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    // When clicked, show a toast with the TextView text
                    String phoneName = (String) parent.getItemAtPosition(position);
                    Toast.makeText(getApplicationContext(),
                            "Connecting to...: " + phoneName,
                            Toast.LENGTH_LONG).show();
                    String phoneAddress = deviceMap.get(phoneName);

                    for(String channel: cam4EventChannels) {
                        try {
                            BluetoothSocket socket = null;
                            if (serverSockets.containsKey(channel)) {
                                socket = serverSockets.get(channel);
                            } else {
                                socket = mBluetoothAdapter.getRemoteDevice(phoneAddress)
                                        .createRfcommSocketToServiceRecord(UUID.fromString(channel));
                                socket.connect();
                                serverSockets.put(channel, socket);
                            }

                            socket.getOutputStream().write("Hello Bluetooth!".getBytes());

                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }



                }
            });

            listPairedDevices(mBluetoothAdapter);

            refreshDeviceList();

            makeAdapterDiscoverable(mBluetoothAdapter);

            //Accept sockets
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for(String channel: cam4EventChannels) {
                        BluetoothSocket acceptedClientSocket = null;
                        BluetoothServerSocket serverSocket = null;
                        try {
                            serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("Cam4Event", UUID.fromString(channel));
                            acceptedClientSocket = serverSocket.accept();

                        } catch (IOException e) {
                            e.printStackTrace();
                            break;
                        }

                        if (acceptedClientSocket != null) {
                            manageConnectedSocket(channel, acceptedClientSocket);
                            try {
                                serverSocket.close();
                                break;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                    }

                }
            }).start();


        }

        registerDiscoveryBroadcastListener();


    }

    private void manageConnectedSocket(String channel, BluetoothSocket acceptedClientSocket) {
        clientSockets.put(channel, acceptedClientSocket);
        new ConnectedThread(acceptedClientSocket).start();
        Log.d("Cam4Event:", "Connected socket to " + channel);

    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    Log.d("Cam4Event:", "Waiting for message...");
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    String message = new String(Arrays.copyOf(buffer, bytes));
                    Log.d("Cam4Event:", "Got message: " + message);

                    mHandler.obtainMessage(0, new StringMessage(mmSocket.getRemoteDevice().getName(), message))
                            .sendToTarget();

                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private void makeAdapterDiscoverable(BluetoothAdapter mBluetoothAdapter) {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent discoverableIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void listPairedDevices(BluetoothAdapter mBluetoothAdapter) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                deviceMap.put(device.getName(),device.getAddress());
            }
        }
    }

    private void registerDiscoveryBroadcastListener() {
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        for(Map.Entry<String, BluetoothSocket> socket: clientSockets.entrySet()) {
            try {
                socket.getValue().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for(Map.Entry<String, BluetoothSocket> socket: serverSockets.entrySet()) {
            try {
                socket.getValue().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
