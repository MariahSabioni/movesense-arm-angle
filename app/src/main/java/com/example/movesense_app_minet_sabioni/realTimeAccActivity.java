package com.example.movesense_app_minet_sabioni;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.movesense_app_minet_sabioni.Computations.TypeConverter;
import com.example.movesense_app_minet_sabioni.RawData.AccData;
import com.example.movesense_app_minet_sabioni.Results.ResultsMethod1;
import com.example.movesense_app_minet_sabioni.Results.ResultsMethod2;
import com.example.movesense_app_minet_sabioni.Utils.MsgUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class realTimeAccActivity extends AppCompatActivity {
    private static final String LOG_TAG = "realTimeAccActivity";

    private LineChart mChart;
    private Thread thread;
    private boolean plotData = true;

    private boolean newConnection = true;

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

    private int mSelectedFreq;
    private int defaultFreq = 13; //default frequency
    private String IMU_COMMAND = "Meas/IMU9/"+defaultFreq; //default subscription
    private final byte MOVESENSE_REQUEST = 1, MOVESENSE_RESPONSE = 2, REQUEST_ID = 99;

    private BluetoothDevice mSelectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;

    private Handler mHandler;

    private Spinner dataTypeSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time_acc);

        //UI
        dataTypeSpinner = findViewById(R.id.spinner_data_type);
        ArrayAdapter<CharSequence> adapter_frequency = ArrayAdapter.createFromResource(this, R.array.dataValues, android.R.layout.simple_spinner_item);
        adapter_frequency.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dataTypeSpinner.setAdapter(adapter_frequency);

        Intent intent = getIntent();
        // Get the selected device from the intent
        mSelectedDevice = intent.getParcelableExtra(SettingsActivity.SELECTED_DEVICE);
        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show();
        }
        mSelectedFreq = intent.getIntExtra(SettingsActivity.FREQUENCY,defaultFreq);

        mHandler = new Handler();

        mChart = (LineChart) findViewById(R.id.realTimeAcc);

        // enable description text
        mChart.getDescription().setEnabled(true);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.BLACK);

        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(true);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMaximum(20f);
        leftAxis.setAxisMinimum(-20f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        mChart.getAxisLeft().setDrawGridLines(true);
        mChart.getXAxis().setDrawGridLines(false);
        mChart.setDrawBorders(false);

        feedMultiple();

    }

    private void addEntry(float x, float y, float z, String dataType) {

        LineData data = mChart.getData();

        if (data != null) {

            ILineDataSet set_x = data.getDataSetByIndex(0);
            ILineDataSet set_y = data.getDataSetByIndex(1);
            ILineDataSet set_z = data.getDataSetByIndex(2);
            // set.addEntry(...); // can be called as well

            if (set_x == null) {
                set_x = createSet(0);
                data.addDataSet(set_x);
            }

            if (set_y == null) {
                set_y = createSet(1);
                data.addDataSet(set_y);
            }

            if (set_z == null) {
                set_z = createSet(2);
                data.addDataSet(set_z);
            }

            if (dataType.equals("")){
                dataType = "Acc";
            }
            set_x.setLabel(dataType +" X");
            set_y.setLabel(dataType +" Y");
            set_z.setLabel(dataType +" Z");


            data.addEntry(new Entry(set_x.getEntryCount(), x ), 0);
            data.addEntry(new Entry(set_y.getEntryCount(), y ), 1);
            data.addEntry(new Entry(set_z.getEntryCount(), z), 2);

            data.notifyDataChanged();

            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(150);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());

        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mSelectedDevice != null) {
            // Connect and register call backs for bluetooth gatt
            Context context = this;
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothGatt =
                            mSelectedDevice.connectGatt(context, false, mBtGattCallback);
                }
            }, 100);

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            try {
                mBluetoothGatt.close();
            } catch (Exception e) {
                // ugly, but this is to handle a bug in some versions in the Android BLE API
            }
        }
    }

    /**
     * Callbacks for bluetooth gatt changes/updates
     * The documentation is not always clear, but most callback methods seems to
     * be executed on a worker thread - hence use a Handler when updating the ui.
     */
    private final BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                mHandler.post(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(getApplicationContext(), "Connected",
                                Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
                // Discover services
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Close connection and display info in ui
                mBluetoothGatt = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(getApplicationContext(), "Disconnected",
                                Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
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
                    // command example: 1, 99, "/Meas/Acc/13"

                    IMU_COMMAND = "Meas/IMU9/" + mSelectedFreq;
                    byte[] command =
                            TypeConverter.stringToAsciiArray(REQUEST_ID, IMU_COMMAND);
                    commandChar.setValue(command);
                    boolean wasSuccess = mBluetoothGatt.writeCharacteristic(commandChar);
                    Log.i(LOG_TAG, "commandChar Subscribe: "+ Arrays.toString(command));
                    Log.i(LOG_TAG, "subscribe writeCharacteristic was success=" + wasSuccess);

                } else {
                    mHandler.post(new Runnable() {
                        public void run() {
                            MsgUtils.createDialog("Alert!",
                                    getString(R.string.service_not_found),
                                    realTimeAccActivity.this)
                                    .show();
                        }
                    });
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicWrite " + characteristic.getUuid().toString());

            // Enable notifications on data from the sensor. First: Enable receiving
            // notifications on the client side, i.e. on this Android device.
            BluetoothGattService movesenseService = gatt.getService(MOVESENSE_2_0_SERVICE);
            BluetoothGattCharacteristic dataCharacteristic =
                    movesenseService.getCharacteristic(MOVESENSE_2_0_DATA_CHARACTERISTIC);
            // second arg: true, notification; false, indication
            boolean success = gatt.setCharacteristicNotification(dataCharacteristic, true);
            if (success) {
                Log.i(LOG_TAG, "setCharactNotification success");
                // Second: set enable notification server side (sensor). Why isn't
                // this done by setCharacteristicNotification - a flaw in the API?
                BluetoothGattDescriptor descriptor =
                        dataCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor); // callback: onDescriptorWrite
            } else {
                Log.i(LOG_TAG, "setCharacteristicNotification failed");
            }
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor
                descriptor, int status) {
            Log.i(LOG_TAG, "onDescriptorWrite, status " + status);

            if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid()))
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // if success, we should receive data in onCharacteristicChanged
                    mHandler.post(new Runnable() {
                        public void run() {
                            Toast toast = Toast.makeText(getApplicationContext(), "Notifications enabled",
                                    Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    });
                }
        }

        /**
         * Callback called on characteristic changes, e.g. when a sensor data value is changed.
         * This is where we receive notifications on new sensor data.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            // debug
            // Log.i(LOG_TAG, "onCharacteristicChanged " + characteristic.getUuid());

            // if response and id matches
            if (MOVESENSE_2_0_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data[0] == MOVESENSE_RESPONSE && data[1] == REQUEST_ID) {
                    // NB! use length of the array to determine the number of values in this
                    // "packet", the number of values in the packet depends on the frequency set(!)
                    int len = data.length;
                    int numOfSamples = (len - 6) / (3*3*4); //3 data types, 3 coordinates, 4 bytes each
                    int offset = 2;
                    int datasize = 4;
                    Log.i(LOG_TAG, String.valueOf(len));
                    // parse and interpret the data, ...
                    int time = TypeConverter.fourBytesToInt(data, offset);
                    float accX = TypeConverter.fourBytesToFloat(data, offset+datasize);
                    float accY = TypeConverter.fourBytesToFloat(data, offset+2*datasize);
                    float accZ = TypeConverter.fourBytesToFloat(data, offset+3*datasize);

                    float gyroX = TypeConverter.fourBytesToFloat(data, offset+datasize+(numOfSamples)*12);
                    float gyroY = TypeConverter.fourBytesToFloat(data, offset+2*datasize+(numOfSamples)*12);
                    float gyroZ = TypeConverter.fourBytesToFloat(data, offset+3*datasize+(numOfSamples)*12);

                    float magX = TypeConverter.fourBytesToFloat(data, offset+datasize+(numOfSamples)*12);
                    float magY = TypeConverter.fourBytesToFloat(data, offset+2*datasize+(numOfSamples)*12);
                    float magZ = TypeConverter.fourBytesToFloat(data, offset+3*datasize+(numOfSamples)*12);

                    String dataType = dataTypeSpinner.getSelectedItem().toString();

                    if(dataType.equals("") || dataType.equals("Acc")){
                        if(plotData){
                            addEntry(accX,accY,accZ,dataType);
                            plotData = false;
                        }
                    } else if (dataType.equals("Gyro")){
                        if(plotData){
                            addEntry(gyroX,gyroY,gyroZ,dataType);
                            plotData = false;
                        }
                    } else if (dataType.equals("Magn")){
                        if(plotData){
                            addEntry(magX,magY,magZ,dataType);
                            plotData = false;
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicRead " + characteristic.getUuid().toString());
        }
    };

    private LineDataSet createSet(int axis) {

        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(3f);
        if (axis==0){
            set.setColor(Color.RED);
            set.setLabel("Acc X");
        }
        else if (axis==1){
            set.setColor(Color.GREEN);
            set.setLabel("Acc Y");

        } else if (axis == 2) {
            set.setColor(Color.BLUE);
            set.setLabel("Acc Z");
        }
        set.setHighlightEnabled(false);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        return set;
    }

    private void feedMultiple() {

        if (thread != null){
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true){
                    plotData = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (thread != null) {
            thread.interrupt();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        thread.interrupt();
        super.onDestroy();
    }
}