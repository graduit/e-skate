package com.jhp.electricskateboard;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.jhp.electricskateboard.helperclasses.TextToSpeechHelper;
import com.jhp.electricskateboard.helperclasses.VibrationHelper;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements SensorEventListener, LocationListener {

    private MainActivityFragment retainedFragment;

    private SensorManager mSensorManager;
    private Sensor mAccelerometerSensor, mGyroscopeSensor, mProximitySensor;

    // Values related to the gyroscope sensor changed event
    // Create a constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private float timestamp;
    private static final float EPSILON = 0.1f;

    private final int REQUEST_CODE_SPEECH_INPUT = 100;
    private String textFromSpeechBackToSpeech = "";

    private final int REQUEST_CODE_FINE_LOCATION = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.jhp.electricskateboard.R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(com.jhp.electricskateboard.R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(com.jhp.electricskateboard.R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                retainedFragment.setEmergencyBrakesOn();
            }
        });

        FloatingActionButton fab2 = (FloatingActionButton) findViewById(com.jhp.electricskateboard.R.id.fab2);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                promptSpeechInput();
            }
        });

        android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
        retainedFragment = (MainActivityFragment) fm.findFragmentById(com.jhp.electricskateboard.R.id.fragment);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        // For measuring speed of phone using GPS
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION },
                    REQUEST_CODE_FINE_LOCATION);
        } else {
            LocationManager lm =(LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            // Note use higher numbers for parameter 2 and 3, to reduce battery usage
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.jhp.electricskateboard.R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == com.jhp.electricskateboard.R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Called from the parent ActivityOverlayWithBluetoothMonitoring.java class
     */
    public void handleUnconnectedBTSocket() {
        retainedFragment.handleUnconnectedBTSocket();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int currentThrottlePosition = retainedFragment.seekbarThrottle.getProgress();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (currentThrottlePosition != retainedFragment.seekbarThrottle.getMax()) {
                    int newThrottlePosition = currentThrottlePosition + 1;
                    retainedFragment.seekbarThrottle.setProgress(newThrottlePosition);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (currentThrottlePosition != 0) {
                    int newThrottlePosition = currentThrottlePosition - 1;
                    retainedFragment.seekbarThrottle.setProgress(newThrottlePosition);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGyroscopeSensor,
                SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mProximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL);

        TextToSpeechHelper.onResumeSlashConvertTextToSpeech(this, textFromSpeechBackToSpeech);

        if (textFromSpeechBackToSpeech != "") {
            textFromSpeechBackToSpeech = "";
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);

        TextToSpeechHelper.onPauseTextToSpeech();
    }

    boolean isDetectedSpeedUpStep1 = false, isDetectedSpeedUpStep2 = false, isDetectedSpeedUpStep3 = false, isDetectedSpeedUpStep4 = false;
    float timestampSpeedUpStep1 = 0, timestampSpeedUpStep2 = 0, timestampSpeedUpStep3 = 0, timestampSpeedUpStep4 = 0;
    boolean isDetectedSlowDownStep1 = false, isDetectedSlowDownStep2 = false, isDetectedSlowDownStep3 = false, isDetectedSlowDownStep4 = false;
    float timestampSlowDownStep1 = 0, timestampSlowDownStep2 = 0, timestampSlowDownStep3 = 0, timestampSlowDownStep4 = 0;
    private static final float SPEEDUP_SLOWDOWN_SINGLEMOTION_TIMELIMIT = 0.33f;
    private static final float SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT = 1.0f;

    int slowDownStep1Count = 0, slowDownStep2Count = 0;
    int speedUpStep1Count = 0, speedUpStep2Count = 0;

    double[] mLinearAcceleration;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            final float alpha = 0.8f;

            // Isolate the force of gravity with the low-pass filter.
            double[] gravity = new double[3];
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            double[] linear_acceleration = new double[3];
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];

            mLinearAcceleration = linear_acceleration;

            retainedFragment.textAccelerometerX.setText(Double.toString(linear_acceleration[0]));
            retainedFragment.textAccelerometerY.setText(Double.toString(linear_acceleration[1]));
            retainedFragment.textAccelerometerZ.setText(Double.toString(linear_acceleration[2]));
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // Usually, the output of the gyroscope is integrated over time to calculate
            // a rotation describing the change of angles over the timestep.
            // This timestep's delta rotation to be multiplied by the current rotation
            // after computing it from the gyro sample data.
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2S;
                // Axis of the rotation sample, not normalized yet.
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];

                // Calculate the angular speed of the sample
                float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

                // Normalize the rotation vector, if it's big enough, to get the axis
                // (that is, EPSILON should represent your maximum allowable margin of error)
                if (omegaMagnitude > EPSILON) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                // Integrate around this axis with the angular speed by the timestep
                // in order to get a delta rotation from this sample over the timestep
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
                float thetaOverTwo = omegaMagnitude * dT / 2.0f;
                float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
                float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
                deltaRotationVector[0] = sinThetaOverTwo * axisX;
                deltaRotationVector[1] = sinThetaOverTwo * axisY;
                deltaRotationVector[2] = sinThetaOverTwo * axisZ;
                deltaRotationVector[3] = cosThetaOverTwo;

                retainedFragment.textGyroscopeX.setText(String.format("%.2f", deltaRotationVector[0]));
                retainedFragment.textGyroscopeY.setText(String.format("%.2f", deltaRotationVector[1]));
                retainedFragment.textGyroscopeZ.setText(String.format("%.2f", deltaRotationVector[2]));

                detectSlowDownMotionType2(event, deltaRotationVector);
                detectSpeedUpMotionType2(event, deltaRotationVector);
            }
            timestamp = event.timestamp;
            float[] deltaRotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
        } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            float distance = event.values[0];
            // Make sure close proximity isn't detected unless phone is upright,
            // i.e. to avoid the detection happening when the phone is simply by your leg in the rest position
            if (mLinearAcceleration != null && mLinearAcceleration[0] > -3 && mLinearAcceleration[0] < 5
                    && mLinearAcceleration[1] > 0) {
                // Galaxy S6 returns a distance of 0.0 if something is close, or 8.0 when nothing is in front of it
                if (distance < 4.0f) { // Arbitrary value 4.0f
                    // TODO Add feature here
                    Toast.makeText(this, "Hello", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Do something here if sensor accuracy changes.
    }


    /**
     * Detect slow down gesture where there is a certain number of repeated motions within totalmotion_timelimit
     * @param event
     * @param deltaRotationVector
     */
    private void detectSlowDownMotionType2(SensorEvent event, float[] deltaRotationVector) {
        isDetectedSlowDownStep1 = deltaRotationVector[1] > 0.1 && deltaRotationVector[2] > 0.01;
        isDetectedSlowDownStep2 = deltaRotationVector[1] < -0.1 && deltaRotationVector[2] < -0.01;
        if (isDetectedSlowDownStep1) {
            if ((slowDownStep1Count == 0 && slowDownStep2Count == 0) ||
                    (slowDownStep1Count == 1 && slowDownStep2Count == 1)) {
                slowDownStep1Count++;
            }
            if (slowDownStep1Count == 1) {
                timestampSlowDownStep1 = event.timestamp;
            } else if ((event.timestamp - timestampSlowDownStep1) * NS2S > SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT) {
                slowDownStep1Count = 0;
                slowDownStep2Count = 0;
                timestampSlowDownStep1 = 0;
            }
        } else if (isDetectedSlowDownStep2) {
            if ((slowDownStep1Count == 1 && slowDownStep2Count == 0) ||
                    (slowDownStep1Count == 2 && slowDownStep2Count == 1)) {
                slowDownStep2Count++;
            }
            if (slowDownStep2Count == 2 && slowDownStep1Count == 2) {
                if ((event.timestamp - timestampSlowDownStep1) * NS2S <= SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT) {
                    // VibrationHelper.vibrate(this);
                    TextToSpeechHelper.convertTextToSpeech(this, "Slow down");

                    int currentThrottlePosition = retainedFragment.seekbarThrottle.getProgress();
                    if (currentThrottlePosition != 0) {
                        int newThrottlePosition = currentThrottlePosition - 3; // Note the minus value is actually the number of steps, not the actually tick value
                        retainedFragment.seekbarThrottle.setProgress(newThrottlePosition);
                    }
                }
                slowDownStep1Count = 0;
                slowDownStep2Count = 0;
                timestampSlowDownStep1 = 0;

                // Reset the potential speed up detection as well
                speedUpStep1Count = 0;
                speedUpStep2Count = 0;
                timestampSpeedUpStep1 = 0;
            }
        } else if ((event.timestamp - timestampSlowDownStep1) * NS2S > SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT) {
            slowDownStep1Count = 0;
            slowDownStep2Count = 0;
            timestampSlowDownStep1 = 0;
        }
    }


    /**
     * Detect speed up gesture where there is a certain number of repeated motions
     * @param event
     * @param deltaRotationVector
     */
    private void detectSpeedUpMotionType2(SensorEvent event, float[] deltaRotationVector) {
        isDetectedSpeedUpStep1 = deltaRotationVector[0] < -0.15 && deltaRotationVector[1] >= -0.15;
        isDetectedSpeedUpStep2 = deltaRotationVector[0] > 0.15 && deltaRotationVector[1] <= 0.15;
        if (isDetectedSpeedUpStep1) {
            if ((speedUpStep1Count == 0 && speedUpStep2Count == 0) ||
                    (speedUpStep1Count == 1 && speedUpStep2Count == 1)) {
                speedUpStep1Count++;
            }
            if (speedUpStep1Count == 1) {
                timestampSpeedUpStep1 = event.timestamp;
            } else if ((event.timestamp - timestampSpeedUpStep1) * NS2S > SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT) {
                speedUpStep1Count = 0;
                speedUpStep2Count = 0;
                timestampSpeedUpStep1 = 0;
            }
        } else if (isDetectedSpeedUpStep2) {
            if ((speedUpStep1Count == 1 && speedUpStep2Count == 0) ||
                    (speedUpStep1Count == 2 && speedUpStep2Count == 1)) {
                speedUpStep2Count++;
            }
            if (speedUpStep2Count == 2 && speedUpStep1Count == 2) {
                if ((event.timestamp - timestampSpeedUpStep1) * NS2S <= SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT) {
                    // VibrationHelper.vibrate(this);
                    TextToSpeechHelper.convertTextToSpeech(this, "Speed up");

                    int currentThrottlePosition = retainedFragment.seekbarThrottle.getProgress();
                    if (currentThrottlePosition != retainedFragment.seekbarThrottle.getMax()) {
                        int newThrottlePosition = currentThrottlePosition + 3;
                        retainedFragment.seekbarThrottle.setProgress(newThrottlePosition);
                    }
                }
                speedUpStep1Count = 0;
                speedUpStep2Count = 0;
                timestampSpeedUpStep1 = 0;

                // Reset the potential slow down detection as well
                slowDownStep1Count = 0;
                slowDownStep2Count = 0;
                timestampSlowDownStep1 = 0;
            }
        } else if ((event.timestamp - timestampSpeedUpStep1) * NS2S > SPEEDUP_SLOWDOWN_TOTALMOTION_TIMELIMIT) {
            speedUpStep1Count = 0;
            speedUpStep2Count = 0;
            timestampSpeedUpStep1 = 0;
        }
    }


    // TODO Use this for speech recognition - Note this causes app to pause...
    /**
     * Shows google speech input dialog
     */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "Oops! Your device does not support Speech to Text",
                    Toast.LENGTH_SHORT).show();
        }
    }


    // Receiving speech input
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String textFromSpeech = result.get(0);
                    // TODO modify this for speech recognition
                    if (textFromSpeech.equals("speed up")) {
                        textFromSpeechBackToSpeech = "Speeding up";
                    } else if (textFromSpeech.equals("slow down")) {
                        textFromSpeechBackToSpeech = "Slowing down";
                    }
                }
                break;
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        // Permission granted here
                        LocationManager lm =(LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                        // Note used higher numbers for parameter 2 and 3, to reduce battery usage
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                    }
                } else {
                    // Permission Denied.
                    // Disable any functionality that depends on this permission.
                }
                return;
            }

        }
    }


    // Determining speed of phone
    @Override
    public void onLocationChanged(Location location) {
        if (location==null){
            // If you can't get speed for some reason
            retainedFragment.setTextOdometer(0);
        } else {
            // int speed=(int) ((location.getSpeed()) is the standard which returns meters per second.
            // Convert to m/s to km/h
            int speed = (int) ((location.getSpeed()*3600)/1000);
            retainedFragment.setTextOdometer(speed);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}