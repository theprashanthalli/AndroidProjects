package com.androjoystick.tv;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
import android.view.View;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.UUID;

public class TVActivity extends Activity {

    private static final String APP_NAME = "AndroJoystickTV";
    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;

    private TextView txtStatus, txtOwnMac;
    private ListView listDevices;
    private Button btnStart;
    private GameView gameView;

    private static final int REQUEST_BT_PERMS = 101;

    @RequiresPermission("android.permission.LOCAL_MAC_ADDRESS")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv);

        txtStatus = findViewById(R.id.txtStatus);
        txtOwnMac = findViewById(R.id.txtMacAddress); // Add this TextView in XML
        listDevices = findViewById(R.id.listDevices);
        btnStart = findViewById(R.id.btnStart);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        requestBluetoothPermissions();

        btnStart.setOnClickListener(v -> startBluetoothServer());
    }

    @RequiresPermission("android.permission.LOCAL_MAC_ADDRESS")
    private void requestBluetoothPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_BT_PERMS);
        } else {
            enableDiscoverableMode();
            showOwnMacAddress();
            listPairedDevices();
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private void enableDiscoverableMode() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);
    }

    @RequiresPermission("android.permission.LOCAL_MAC_ADDRESS")
    private void showOwnMacAddress() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String mac = bluetoothAdapter.getAddress();
                txtOwnMac.setText("TV MAC: " + mac);
            } else {
                txtOwnMac.setText("MAC: [permission denied]");
            }
        } catch (Exception e) {
            txtOwnMac.setText("MAC: [error]");
        }
    }

    private void listPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return;

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        for (BluetoothDevice device : pairedDevices) {
            adapter.add(device.getName() + "\n" + device.getAddress());
        }
        listDevices.setAdapter(adapter);
    }

    private void startBluetoothServer() {
        txtStatus.setText("Waiting for Bluetooth connection...");
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> txtStatus.setText("Missing Bluetooth CONNECT permission"));
                    return;
                }

                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID);
                socket = serverSocket.accept();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
                    txtStatus.setText("Connected to: " + socket.getRemoteDevice().getName());
                    loadGameView();
                });

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    final String msg = line.trim();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Received: " + msg, Toast.LENGTH_SHORT).show();
                        if (msg.startsWith("JOY")) {
                            String[] coords = msg.split(":")[1].split(",");
                            float x = Float.parseFloat(coords[0]);
                            float y = Float.parseFloat(coords[1]);
                            gameView.updateJoystickPosition(x, y);
                        } else if (msg.equals("ACTION:FIRE")) {
                            gameView.showFireEffect();
                        } else if (msg.equals("ACTION:JUMP")) {
                            gameView.showJumpEffect();
                        }
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> txtStatus.setText("Connection failed: " + e.getMessage()));
            }
        }).start();
    }

    private void loadGameView() {
        FrameLayout container = findViewById(R.id.gameContainer);
        gameView = new GameView(this);
        container.removeAllViews();
        container.addView(gameView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADVERTISE, "android.permission.LOCAL_MAC_ADDRESS"})
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_BT_PERMS) {
            boolean granted = true;
            for (int result : results) {
                if (result != PackageManager.PERMISSION_GRANTED) granted = false;
            }
            if (granted) {
                enableDiscoverableMode();
                showOwnMacAddress();
                listPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}
