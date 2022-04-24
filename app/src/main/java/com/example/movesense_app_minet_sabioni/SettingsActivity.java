package com.example.movesense_app_minet_sabioni;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.example.movesense_app_minet_sabioni.Computations.CSVWriterForResults;
import com.example.movesense_app_minet_sabioni.Utils.MsgUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SettingsActivity";
    private Spinner frequencySpinner;
    private Spinner sensorSpinner;
    private Button button;
    private BluetoothDevice mSelectedDevice = null;
    private int mSelectedFreq = 13;
    private String mSelectedSensor = "IMU9";
    public static String SELECTED_DEVICE = "Selected device";
    public static String FREQUENCY = "Frequency";
    public static String SENSOR = "Sensor";

    // Movesense 2.0 UUIDs (should be placed in resources file)
    public static final UUID MOVESENSE_2_0_SERVICE =
            UUID.fromString("34802252-7185-4d5d-b431-630e7050e8f0");
    public static final UUID MOVESENSE_2_0_COMMAND_CHARACTERISTIC =
            UUID.fromString("34800001-7185-4d5d-b431-630e7050e8f0");
    public static final UUID MOVESENSE_2_0_DATA_CHARACTERISTIC =
            UUID.fromString("34800002-7185-4d5d-b431-630e7050e8f0");
    // UUID for the client characteristic, which is necessary for notifications
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt mBluetoothGattUnsubscribe = null;

    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //UI
        frequencySpinner = findViewById(R.id.frequency_spinner);
        ArrayAdapter<CharSequence> adapter_frequency = ArrayAdapter.createFromResource(this, R.array.frequencyValues, android.R.layout.simple_spinner_item);
        adapter_frequency.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencySpinner.setAdapter(adapter_frequency);
        sensorSpinner = findViewById(R.id.sensor_spinner);
        ArrayAdapter<CharSequence> adapter_sensor = ArrayAdapter.createFromResource(this, R.array.sensorValues, android.R.layout.simple_spinner_item);
        adapter_sensor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sensorSpinner.setAdapter(adapter_sensor);
        button = findViewById(R.id.button_go);

        Intent intent = getIntent();
        // Get the selected device from the intent
        mSelectedDevice = intent.getParcelableExtra(ScanActivity.SELECTED_DEVICE);
        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show();
            //mDeviceView.setText(R.string.no_device);
        } else {
            //mDeviceView.setText(mSelectedDevice.getName());
        }

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSettingsGo();
            }
        });

        mHandler = new Handler();

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mSelectedDevice != null) {
            // Connect and register call backs for bluetooth gatt
            Context context = this;
            final Handler handler = new Handler(Looper.getMainLooper());
            //unsubscribe before setting a new connection
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothGattUnsubscribe =
                            mSelectedDevice.connectGatt(context, false, mBtGattCallbackUnsubscribe);
                }
            }, 100);
        }
    }

    private final BluetoothGattCallback mBtGattCallbackUnsubscribe = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGattUnsubscribe = gatt;
                // Discover services
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Close connection and display info in ui
                mBluetoothGattUnsubscribe = null;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Debug: list discovered services
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.i(LOG_TAG, service.getUuid().toString());
                }

                // Get the Movesense 2.0 IMU service
                BluetoothGattService movesenseService = gatt.getService(MOVESENSE_2_0_SERVICE);
                if (movesenseService != null) {
                    // debug: service present, list characteristics
                    List<BluetoothGattCharacteristic> characteristics =
                            movesenseService.getCharacteristics();
                    for (BluetoothGattCharacteristic chara : characteristics) {
                        Log.i(LOG_TAG, chara.getUuid().toString());
                    }

                    // Write a command, as a byte array, to the command characteristic
                    // Callback: onCharacteristicWrite
                    BluetoothGattCharacteristic commandChar =
                            movesenseService.getCharacteristic(
                                    MOVESENSE_2_0_COMMAND_CHARACTERISTIC);

                    //write unsubscribe every time a new connection is set
                    //if (newConnection){
                    byte[] unsubscribeCommand = new byte[2];
                    unsubscribeCommand[0] = 2;
                    unsubscribeCommand[1] = 99;
                    commandChar.setValue(unsubscribeCommand);
                    boolean wasSuccess = mBluetoothGattUnsubscribe.writeCharacteristic(commandChar);
                    Log.i(LOG_TAG, "commandChar Unsubscribe: "+ Arrays.toString(unsubscribeCommand));
                    Log.i(LOG_TAG, "unsubscribe writeCharacteristic was success=" + wasSuccess);
                    mBluetoothGattUnsubscribe.disconnect();
                    mBluetoothGattUnsubscribe.close();
                    //}
                }
            }
        }
    };



    @Override
    protected void onStop() {
        super.onStop();
        if (mBluetoothGattUnsubscribe != null) {
            mBluetoothGattUnsubscribe.disconnect();
            try {
                mBluetoothGattUnsubscribe.close();
            } catch (Exception e) {
                // ugly, but this is to handle a bug in some versions in the Android BLE API
            }
        }
    }
    private void onSettingsGo() {
        //BluetoothDevice selectedDevice = mDeviceList.get(position);
        // BluetoothDevice objects are parceable, i.e. we can "send" the selected device
        // to the DeviceActivity packaged in an intent.
        mSelectedFreq = Integer.parseInt(frequencySpinner.getSelectedItem().toString());
        mSelectedSensor = sensorSpinner.getSelectedItem().toString();
        Intent intent = new Intent(SettingsActivity.this, DeviceActivity.class);
        intent.putExtra(SELECTED_DEVICE, mSelectedDevice);
        intent.putExtra(FREQUENCY, mSelectedFreq);
        intent.putExtra(SENSOR, mSelectedSensor);
        startActivity(intent);
    }
}
