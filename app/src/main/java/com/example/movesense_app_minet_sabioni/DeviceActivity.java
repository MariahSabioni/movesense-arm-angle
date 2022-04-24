package com.example.movesense_app_minet_sabioni;

import static com.example.movesense_app_minet_sabioni.Utils.MsgUtils.showToast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.UUID;

import com.example.movesense_app_minet_sabioni.Computations.CSVWriterForResults;
import com.example.movesense_app_minet_sabioni.RawData.AccData;
import com.example.movesense_app_minet_sabioni.Results.ResultsMethod1;
import com.example.movesense_app_minet_sabioni.Results.ResultsMethod2;
import com.example.movesense_app_minet_sabioni.Utils.MsgUtils;
import com.example.movesense_app_minet_sabioni.Computations.TypeConverter;

public class DeviceActivity extends AppCompatActivity {

    //UI
    private TextView angleElevationMethod1;
    private TextView angleElevationMethod2;
    private TextView mDeviceView;
    private TextView mStatusView;
    private TextView mFrequencyView;
    private TextView mSensorView;
    private ImageButton recordingButton;
    private Drawable startRecordDrawable;
    private Drawable stopRecordDrawable;
    private boolean recordPressed;
    private EditText recordTime;
    private TextView AccView;
    private TextView GyroView;
    private TextView MagnView;

    //Audio
    private TextToSpeech textToSpeech;
    private static final int utteranceId = 42;

    //debug
    private static final String LOG_TAG = "DeviceActivity";

    // Data
    private AccData accData;
    private AccData accDataPrev = null;
    private ResultsMethod1 resultsMethod1;
    private ResultsMethod2 resultsMethod2;
    private ResultsMethod2 resultsMethod2Prev = null;
    private double alpha = 0.4;
    private double freq = 13; //default frequency
    private double dT = 1/freq;
    private double beta = 0.8;
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
    private String mSelectedSensor;
    private int defaultFreq = 13; //default frequency
    private String defaultSensor = "IMU9"; //default sensor
    private String IMU_COMMAND = "Meas/"+defaultSensor+"/"+defaultFreq; //default subscription
    private final byte MOVESENSE_REQUEST = 1, MOVESENSE_RESPONSE = 2, REQUEST_ID = 99;
    public static String SELECTED_DEVICE = "Selected device";
    public static String FREQUENCY = "Frequency";

    private BluetoothDevice mSelectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGatt mBluetoothGattUnsubscribe = null;

    private Handler mHandler;
    //CSV
    private CSVWriterForResults csvWriterM1;
    private CSVWriterForResults csvWriterM2;
    private Handler handlerCSV;
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        //UI
        mDeviceView = findViewById(R.id.device_view);
        mStatusView =findViewById(R.id.status_view);
        mFrequencyView = findViewById(R.id.current_freq);
        mSensorView = findViewById(R.id.current_sensor);
        angleElevationMethod1 = findViewById(R.id.elevationAngleMethod1);
        angleElevationMethod2 = findViewById(R.id.elevationAngleMethod2);
        mSensorView = findViewById(R.id.current_sensor);
        AccView = findViewById(R.id.acc_view);
        GyroView = findViewById(R.id.gyro_view);
        MagnView = findViewById(R.id.magn_view);


        //Recording + CSV
        recordingButton = findViewById(R.id.recording_button);
        recordTime = findViewById(R.id.recordingTime);
        // load drawables (images)
        Resources resources = getResources();
        startRecordDrawable = ResourcesCompat.getDrawable(resources, R.drawable.start_record_icon, null);
        stopRecordDrawable = ResourcesCompat.getDrawable(resources, R.drawable.stop_record_icon, null);

        //Default UI
        recordingButton.setBackground(startRecordDrawable);
        recordPressed = false;

        //Init CSV
        csvWriterM1 = new CSVWriterForResults();
        csvWriterM2 = new CSVWriterForResults();
        handlerCSV = new Handler();

        Intent intent = getIntent();
        // Get the selected device, frequency and sensor from the intent
        mSelectedDevice = intent.getParcelableExtra(SettingsActivity.SELECTED_DEVICE);
        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show();
            mDeviceView.setText(R.string.no_device);
        } else {
            mDeviceView.setText(mSelectedDevice.getName());
        }
        mSelectedFreq = intent.getIntExtra(SettingsActivity.FREQUENCY,defaultFreq);
        String mSelectedFreqStr = String.valueOf(mSelectedFreq);
        mFrequencyView.setText("Current frequency: " + mSelectedFreqStr);

        mSelectedSensor = intent.getStringExtra(SettingsActivity.SENSOR);
        mSensorView.setText("Current sensor: " + mSelectedSensor);

        mHandler = new Handler();

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mSelectedDevice != null) {
            // Connect and register call backs for bluetooth gatt
            Context context = this;
            final Handler handler = new Handler(Looper.getMainLooper());
            //subscribe
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

    @Override
    protected void onPause() {
        super.onPause();
        handlerCSV.removeCallbacks(saveCSV);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Initialize the text-to-speech service - we do this initialization
        // in onResume because we shutdown the service in onPause
        textToSpeech = new TextToSpeech(getApplicationContext(),
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status == TextToSpeech.SUCCESS) {
                            textToSpeech.setLanguage(Locale.US);
                        }
                    }
                });
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
                        mStatusView.setText(R.string.connected);
                    }
                });
                // Discover services
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Close connection and display info in ui
                mBluetoothGatt = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        mStatusView.setText(R.string.disconnected);
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

                    IMU_COMMAND = "Meas/"+mSelectedSensor+"/"+ mSelectedFreq;
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
                                    DeviceActivity.this)
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
                    Log.i(LOG_TAG, "notifications enabled" + status);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(DeviceActivity.this, "Notifications enabled", Toast.LENGTH_SHORT).show();
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
            Log.i(LOG_TAG, "onCharacteristicChanged " + characteristic.getUuid());

            // if response and id matches
            if (MOVESENSE_2_0_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data[0] == MOVESENSE_RESPONSE && data[1] == REQUEST_ID) {
                    // NB! use length of the array to determine the number of values in this
                    // "packet", the number of values in the packet depends on the frequency set(!)
                    int len = data.length;
                    int sensorNum = 3;
                    if (mSelectedSensor.equals("IMU9")){sensorNum = 3;}
                    else if(mSelectedSensor.equals("IMU6")){sensorNum = 2;}
                    int offset = 2;
                    int dataSize = 4;
                    int numOfSamples = (len - 6) / (sensorNum*3*dataSize); //sensorNum data types, 3 coordinates, 4 bytes each
                    Log.i(LOG_TAG, String.valueOf(len));
                    // parse and interpret the data, ...
                    int time = TypeConverter.fourBytesToInt(data, offset);
                    float accX = TypeConverter.fourBytesToFloat(data, offset+dataSize);
                    float accY = TypeConverter.fourBytesToFloat(data, offset+2*dataSize);
                    float accZ = TypeConverter.fourBytesToFloat(data, offset+3*dataSize);

                    float gyroX = TypeConverter.fourBytesToFloat(data, offset+dataSize+(numOfSamples)*12);
                    float gyroY = TypeConverter.fourBytesToFloat(data, offset+2*dataSize+(numOfSamples)*12);
                    float gyroZ = TypeConverter.fourBytesToFloat(data, offset+3*dataSize+(numOfSamples)*12);

                    float magX = 0;
                    float magY = 0;
                    float magZ = 0;

                    if (mSelectedSensor.equals("IMU9")) {
                        magX = TypeConverter.fourBytesToFloat(data, offset + dataSize + (numOfSamples) * 12);
                        magY = TypeConverter.fourBytesToFloat(data, offset + 2 * dataSize + (numOfSamples) * 12);
                        magZ = TypeConverter.fourBytesToFloat(data, offset + 3 * dataSize + (numOfSamples) * 12);

                        String magXStr = String.valueOf(magX);
                        String magYStr = String.valueOf(magY);
                        String magZStr = String.valueOf(magZ);
                        String magStr = "X: " + magXStr.substring(0, Math.min(5,magXStr.length())) + "      Y: " + magYStr.substring(0, Math.min(5,magYStr.length())) + "      Z: " + magZStr.substring(0, Math.min(5,magZStr.length()));
                        Log.i(LOG_TAG, "time: " + time + " mag: " + magStr);
                        MagnView.setText("Mag: "+magStr);
                    }

                    //log
                    String accXStr = String.valueOf(accX);
                    String accYStr = String.valueOf(accY);
                    String accZStr = String.valueOf(accZ);
                    String accStr = "X: " + accXStr.substring(0, Math.min(5,accXStr.length()))  + "      Y: " + accYStr.substring(0, Math.min(5,accYStr.length())) + "      Z: " + accZStr.substring(0, Math.min(5,accZStr.length()));
                    Log.i(LOG_TAG, "time: " + time + " acc: " + accStr);
                    String gyroXStr = String.valueOf(gyroX);
                    String gyroYStr = String.valueOf(gyroY);
                    String gyroZStr = String.valueOf(gyroZ);
                    String gyroStr = "X: " + gyroXStr.substring(0, Math.min(5,gyroXStr.length())) + "      Y: " + gyroYStr.substring(0, Math.min(5,gyroYStr.length())) + "      Z: " + gyroZStr.substring(0, Math.min(5,gyroZStr.length()));
                    Log.i(LOG_TAG, "time: " + time + " gyro: " + gyroStr);

                    //Update UI
                    AccView.setText("Acc: "+accStr);
                    GyroView.setText("Gyro: "+gyroStr);


                    //create data object
                    accData = new AccData(accX, accY, accZ, time, alpha);
                    accData.setGyro_x(gyroX);
                    accData.setGyro_y(gyroY);
                    accData.setGyro_z(gyroZ);
                    //filter acc data
                    accData = AccData.filterAcc(accData, accDataPrev);
                    Log.i(LOG_TAG,"raw ax: "+accData.getAcc_x()+" / filtered ax: "+
                            accData.getAcc_xFiltered());
                    accDataPrev = accData;
                    //calculate elevation with method 1
                    resultsMethod1 = new ResultsMethod1(accData);
                    Log.i(LOG_TAG,"results method 1: "+resultsMethod1.getElevation());
                    //calculate elevation with method 2
                    resultsMethod2 = new ResultsMethod2(accData, dT, beta);
                    //apply filter
                    resultsMethod2 = ResultsMethod2.filterPitch(resultsMethod2, resultsMethod2Prev);
                    Log.i(LOG_TAG,"raw pitch: "+resultsMethod2.getPitchAcc()+" / filtered pitch: "+
                            resultsMethod2.getCompPitch());
                    resultsMethod2Prev = resultsMethod2;

                    //record data to csv
                    if (recordPressed) {
                        csvWriterM1.convertResults2String_1(resultsMethod1);
                        csvWriterM2.convertResults2String_2(resultsMethod2);
                    }

                    //update ui
                    double elevationAngleM1 = resultsMethod1.getElevation();
                    final String elevationAngleM1Str = String.valueOf(elevationAngleM1).substring(0, 6);
                    double compPitch = resultsMethod2.getCompPitch();
                    final String elevationAngleM2Str = String.valueOf(compPitch).substring(0, 6);
                    mHandler.post(new Runnable() {
                        public void run() {
                            angleElevationMethod1.setText(elevationAngleM1Str+"°");
                            angleElevationMethod2.setText(elevationAngleM2Str+"°");
                        }
                    });
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicRead " + characteristic.getUuid().toString());
        }
    };

    public void realTimeAccelerometer(View view) {
        Intent intent = new Intent(DeviceActivity.this, realTimeAccActivity.class);
        intent.putExtra(SELECTED_DEVICE, mSelectedDevice);
        intent.putExtra(FREQUENCY, mSelectedFreq);
        startActivity(intent);
    }

    //Recording data
    public void startRecording(View view) throws IOException {
        if (!recordPressed){
            recordingButton.setBackground(stopRecordDrawable);
            recordPressed = true;
            sayIt("Recording in progress");
            String recordTimeStr = recordTime.getText().toString();
            if (recordTimeStr.isEmpty()){
                handlerCSV.postDelayed(saveCSV, 10000);
            }else {
                double recordTime = Double.valueOf(recordTimeStr);
                handlerCSV.postDelayed(saveCSV, (long) (1000*recordTime));
            }
        }else {
            recordingButton.setBackground(startRecordDrawable);
            recordPressed = false;
            handlerCSV.removeCallbacks(saveCSV);
            csvWriterM1.writeToCSV(getApplicationContext(),"Recorded_data_Movesense_1");
            csvWriterM2.writeToCSV(getApplicationContext(),"Recorded_data_Movesense_2");
            //Reset csvWriter
            csvWriterM1 = new CSVWriterForResults();
            csvWriterM2 = new CSVWriterForResults();
            sayIt("Recording stopped");
        }
    }

    //Text to speech
    private void sayIt(String utterance) {
        textToSpeech.speak(utterance, TextToSpeech.QUEUE_FLUSH,
                null, new String("" + utteranceId));
    }

    //Save data in CSV
    private final Runnable saveCSV = new Runnable() {
        @Override
        public void run() {
            recordPressed = false;
            recordingButton.setBackground(startRecordDrawable);
            sayIt("Recording stopped");
            try {
                csvWriterM1.writeToCSV(getApplicationContext(),"Recorded_data_Movesense1");
                csvWriterM2.writeToCSV(getApplicationContext(),"Recorded_data_Movesense2");
            } catch (IOException e) {
                e.printStackTrace();
                Log.i(LOG_TAG, "error recording");
            }
            //Reset csvWriter
            csvWriterM1 = new CSVWriterForResults();
            csvWriterM2 = new CSVWriterForResults();
        }
    };
}
