package com.jhp.electricskateboard;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jhp.electricskateboard.helperclasses.CustomFontHelper;
import com.jhp.electricskateboard.helperclasses.IgnitionSoundHelper;
import com.jhp.electricskateboard.helperclasses.TorchHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/**
 * Retained fragment that connects to the Arduino Bluetooth.
 */
public class MainActivityFragment extends Fragment {

    public boolean hasBluetoothOnOffDialogBeenOpened = false;

    public MainActivityFragment() {
    }

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;

    private static final String TAG = "E-Skateboard Fragment";
    public static final String BT_DISCONNECTED_DIALOG_FRAGMENT_TAG = "BT DCed Dialog Frag Tag";

    Button buttonOn, buttonOff;
    ImageView imageLed;
    TextView textOdometerHundreds, textOdometerTens, textOdometerOnes,
            textThrottle;
    SeekBar seekbarThrottle;

    TextView textAccelerometerX, textAccelerometerY, textAccelerometerZ,
            textGyroscopeX, textGyroscopeY, textGyroscopeZ;

    TextView textReceived;
    Handler bluetoothDataReceivedHandler;
    final int handlerState = 0; // Used to identify handler message
    private static StringBuilder receivedDataString;
    private ConnectToArduinoBluetoothTask mConnectingTask;
    private ConnectedThread mConnectedThread;
    /** Used to destroy the ConnectedThread upon screen rotation/exiting the app */
    public boolean destroyConnectedThread = false;
    /** Used as the synchronization lock to be used between the ConnectedThread and MainThread, i.e. via lock.wait() and lock.notify() */
    static Object lock = new Object();
    /** Used to pause/resume the connected thread (value false and true respectively) */
    private boolean pauseConnectedThread = false;

    ProgressDialog progressDialog;

    // Well known SPP UUID
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // TODO Insert your bluetooth devices MAC address
    private static final String MAC_ADDRESS = "00:00:00:00:00:00";

    /**
     * Used in connectingThread's run method to send a message that shows in a Toast in the main thread
     */
    Handler errorHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            errorExit("Fatal Error", (String) msg.obj);
        }
    };

    // As set in the Arduino code
    private static final String TRANSMISSION_START_INDICATOR = "#";
    private static final String TRANSMISSION_EOL_INDICATOR = "~";

    /**
     * dataToArduino[0] - "P" = Paused, "R" = Resumed
     * dataToArduino[1] - "0" = LED off, "1" = LED on
     * dataToArduino[2-4] - Percentage of throttle , 0 to 100
     */
    private String dataToArduino = "R0000";

    private static final String DATA_FROM_ARDUINO_WHEN_ARDUINO_POWERED_DOWN = "Arduino Powered Down";
    private static final String DATA_TO_ARDUINO_REQUIRED_FOR_RESET_WHEN_ARDUINO_IS_POWERED_DOWN = "R0000";

    private static final String DATA_TO_ARDUINO_EMERGENCY_BRAKE_SIGNAL = "ZZZ"; // Replaces the throttle percentage part of dataToArduino

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        // TODO Comment out, or delete this line after you have updated the MAC_ADDRESS
        getBtMacAddresses();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(com.jhp.electricskateboard.R.layout.fragment_main, container, false);

        receivedDataString = new StringBuilder();

        buttonOn = (Button) view.findViewById(com.jhp.electricskateboard.R.id.buttonOn);
        buttonOff = (Button) view.findViewById(com.jhp.electricskateboard.R.id.buttonOff);
        textReceived = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.textDisplay);
        imageLed = (ImageView) view.findViewById(com.jhp.electricskateboard.R.id.imageLed);

        textOdometerHundreds = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.textOdometerHundreds);
        textOdometerTens = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.textOdometerTens);
        textOdometerOnes = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.textOdometerOnes);

        TextView textOdomterBackingHundreds = ((TextView) view.findViewById(com.jhp.electricskateboard.R.id.textOdometerHundredsBacking));
        TextView textOdomterBackingTens = ((TextView) view.findViewById(com.jhp.electricskateboard.R.id.textOdometerTensBacking));
        TextView textOdomterBackingOnes = ((TextView) view.findViewById(com.jhp.electricskateboard.R.id.textOdometerOnesBacking));
        TextView textKph = ((TextView) view.findViewById(com.jhp.electricskateboard.R.id.textKph));
        CustomFontHelper.setCustomFont(textOdomterBackingHundreds, "fonts/digital-7 (italic).ttf", getActivity());
        CustomFontHelper.setCustomFont(textOdomterBackingTens, "fonts/digital-7 (italic).ttf", getActivity());
        CustomFontHelper.setCustomFont(textOdomterBackingOnes, "fonts/digital-7 (italic).ttf", getActivity());
        CustomFontHelper.setCustomFont(textKph, "fonts/digital-7 (italic).ttf", getActivity());
        CustomFontHelper.setCustomFont(textOdometerHundreds, "fonts/digital-7 (italic).ttf", getActivity());
        CustomFontHelper.setCustomFont(textOdometerTens, "fonts/digital-7 (italic).ttf", getActivity());
        CustomFontHelper.setCustomFont(textOdometerOnes, "fonts/digital-7 (italic).ttf", getActivity());
        setTextOdometer(0);
        textThrottle = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.seekBarMotorSpeedText);
        seekbarThrottle = (SeekBar) view.findViewById(com.jhp.electricskateboard.R.id.seekBarMotorSpeed);
        seekbarThrottle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float motorSpeedPercentageFloat = (progress/(float) seekBar.getMax()) * 100;
                int motorSpeedPercentageInt = Math.round(motorSpeedPercentageFloat);
                String motorSpeedPercentage = motorSpeedPercentageInt + "%\nWOT";
                textThrottle.setText(motorSpeedPercentage);

                setDataToArduinoThrottlePercentage(motorSpeedPercentageInt);
                // Send the message immediately after a change in speed requested (otherwise it would only go through on the 1 second poll)
                if (btSocket != null && btSocket.isConnected()) {
                    if (mConnectedThread != null) {
                        mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        textAccelerometerX = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.accelerometerX);
        textAccelerometerY = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.accelerometerY);
        textAccelerometerZ = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.accelerometerZ);
        textGyroscopeX = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.gyroscopeX);
        textGyroscopeY = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.gyroscopeY);
        textGyroscopeZ = (TextView) view.findViewById(com.jhp.electricskateboard.R.id.gyroscopeZ);

        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setMessage("Connecting to your bluetooth device...");
        }
        if (btAdapter == null)
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btSocket == null)
            checkBTState(); // See below

        buttonOn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (btSocket != null && btSocket.isConnected()) {
                    setDataToArduinoLedState("1");
                    mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR); //see below
                    Toast msg = Toast.makeText(getActivity(),
                            "You have clicked On", Toast.LENGTH_SHORT);
                    msg.show();
                    imageLed.setBackground(ContextCompat.getDrawable(getActivity(), com.jhp.electricskateboard.R.drawable.led_on));
                }
                imageLed.setBackground(ContextCompat.getDrawable(getActivity(), com.jhp.electricskateboard.R.drawable.led_on));

                TorchHelper.turnOnTorch(getActivity());
            }
        });

        buttonOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (btSocket != null && btSocket.isConnected()) {
                    setDataToArduinoLedState("0");
                    mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);
                    Toast msg = Toast.makeText(getActivity(),
                            "You have clicked Off", Toast.LENGTH_SHORT);
                    msg.show();
                    imageLed.setBackground(ContextCompat.getDrawable(getActivity(), com.jhp.electricskateboard.R.drawable.led_off));
                }
                imageLed.setBackground(ContextCompat.getDrawable(getActivity(), com.jhp.electricskateboard.R.drawable.led_off));

                TorchHelper.turnOffTorch(getActivity());
            }
        });

        bluetoothDataReceivedHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) { // If message is what we want
                    synchronized(lock) {
                        String readMessage = (String) msg.obj; // msg.arg1 = bytes from connect thread
                        receivedDataString.append(readMessage); // Keep appending to string until TRANSMISSION_EOL_INDICATOR
                        int endOfLineIndex = receivedDataString.indexOf(TRANSMISSION_EOL_INDICATOR);
                        if (endOfLineIndex > 0) { // Make sure there is data before end of line
                            // Extract string (endOfLineIndex => the starting index of the next character)
                            String dataInPrint = receivedDataString.substring(0, endOfLineIndex);
                            textReceived.setText("Data Received = " + dataInPrint);
	                        if (receivedDataString.charAt(0) == '#') { // If it starts with # we know it is what we are looking for
                                if (receivedDataString.toString().equals((TRANSMISSION_START_INDICATOR + DATA_FROM_ARDUINO_WHEN_ARDUINO_POWERED_DOWN + TRANSMISSION_EOL_INDICATOR))) {
                                    dataToArduino = DATA_TO_ARDUINO_REQUIRED_FOR_RESET_WHEN_ARDUINO_IS_POWERED_DOWN;
                                    mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);
                                    seekbarThrottle.setProgress(0);
                                } else {
                                    // LED sensor
                                    String sensor1 = receivedDataString.substring(1, 2); // Get sensor value from string between indices 1-2
                                    String sensor2 = receivedDataString.substring(2, 5);
                                    if (sensor1.equals("0")) {
                                        imageLed.setBackground(ContextCompat.getDrawable(getActivity(), com.jhp.electricskateboard.R.drawable.led_off));
                                    }
                                }
	                        }
                            receivedDataString.delete(0, receivedDataString.length()); // Clear all string data
                        }
                        lock.notify(); // Wakes up the lock.wait() set in "ConnectedThread".
                    }
                }
            }
        };

        // Keep the screen on
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();

        if (btSocket != null && btSocket.isConnected()) {
            if (mConnectedThread != null) {
                setDataToArduinoResumePausePoll("R");
                mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);
            }
        }

        if (mConnectingTask != null && mConnectingTask.getStatus() == AsyncTask.Status.RUNNING) {
            if (!progressDialog.isShowing())
                progressDialog.show();
        }

        pauseConnectedThread = false;
    }

    @Override
    public void onPause() {
        super.onPause();

        if (progressDialog != null && progressDialog.isShowing())
            progressDialog.dismiss();

        if (btAdapter != null && btAdapter.isEnabled()) {
            if (btSocket != null && btSocket.isConnected()) {
                if (mConnectedThread != null && mConnectedThread.mmOutStream != null) {
                    try {
                        // Let the Arduino know to stop/pause transmitting feedback data
                        setDataToArduinoResumePausePoll("P");
                        mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);
                        mConnectedThread.mmOutStream.flush();
                    } catch (IOException e) {
                        errorExit("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
                    }
                }
            }
        }

        pauseConnectedThread = true;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (this.btSocket != null) {
                this.btSocket.close();
            }
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onDestroy() and failed to close socket." + e2.getMessage() + ".");
        }
        destroyConnectedThread = true;
    }

    /**
     * Check for Bluetooth support and then check to make sure it is turned on.
     * If it isn't on, prompt the user
     */
    private void checkBTState() {
        // Emulator doesn't support Bluetooth and will return null
        if (btAdapter == null) {
            errorExit("Fatal Error", "Bluetooth Not supported. Aborting.");
        } else {
            if (btAdapter.isEnabled()) {
                try {
                    BluetoothDevice device = btAdapter.getRemoteDevice(MAC_ADDRESS);
                    mConnectingTask = new ConnectToArduinoBluetoothTask(getActivity(), device);
                    mConnectingTask.execute();
                } catch (IllegalArgumentException e) {
                    errorExit("Fatal Error", "Problem with mac address\n" + e.toString());
                }
            } else {
                if (!hasBluetoothOnOffDialogBeenOpened) {
                    //Prompt user to turn on Bluetooth
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    hasBluetoothOnOffDialogBeenOpened = true;
                }
            }
        }
    }

    // Handle response from bluetooth on/off dialog
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.e("Bluetooth Status", "Enabled"); // After this, onResume is called
                checkBTState();
            }
            // If there's no result
            if (resultCode == RESULT_CANCELED) {
                // Activity will essentially be useless without bluetooth so just close the activity
                // getActivity().finish();
                // TODO - uncomment the above line to return to simply close the activity
                // TODO   otherwise use this as a cheap way to have "test mode"
                Toast.makeText(getActivity(), "You have entered test mode", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void errorExit(String title, String message){
        Toast msg = Toast.makeText(getActivity(),
                title + " - " + message, Toast.LENGTH_LONG);
        msg.show();
        getActivity().finish();
    }

    /**
     * Currently only called from within the ActivityOverlayWithBluetoothMonitoring.java file
     * @throws IOException
     */
    public void handleUnconnectedBTSocket() {
        try {
            if (btSocket != null)
                btSocket.close();
        } catch (IOException e) {
            errorExit("Fatal Error", "In handleUnconnectedBTSocket() and failed to close sockets." + "\n" + e.toString());
        }

        if (getFragmentManager().findFragmentByTag(BT_DISCONNECTED_DIALOG_FRAGMENT_TAG) == null) {
            // This will force activity close
            BTDisconnectedDialogFragment btDisconnectedDialogFragment = new BTDisconnectedDialogFragment();
            btDisconnectedDialogFragment.setCancelable(false);
            btDisconnectedDialogFragment.show(getActivity().getFragmentManager(), BT_DISCONNECTED_DIALOG_FRAGMENT_TAG);

            // A little something extra to prevent the ConnectedThread continuously running when
            // the bluetooth socket is disconnected.
            destroyConnectedThread = true;
        }
    }

    private class ConnectToArduinoBluetoothTask extends AsyncTask<Void, Void, Boolean> {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        Context context;

        public ConnectToArduinoBluetoothTask(Context context, BluetoothDevice device) {
            this.context = context;
            mmDevice = device;
            BluetoothSocket temp = null;
            try {
                // Two things are needed to make a connection:
                // 1. A MAC address, which we got above (i.e. used to create the BluetoothDevice object).
                // 2. A Service ID or UUID. In this case we are using the UUID for SPP (Serial Port Protocol).
                temp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                // Note: errorExit is fine to call outside of the run() method
                errorExit("Fatal Error", "In ConnectingThread and socket creation failed." + "\n" + e.toString());
                cancel(true);
            }
            mmSocket = temp;

            btSocket = mmSocket;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!progressDialog.isShowing())
                progressDialog.show();
        }

        protected Boolean doInBackground(Void... voids) {
            // Discovery is resource intensive.  Make sure it isn't going on
            // when you attempt to connect and pass your message.
            btAdapter.cancelDiscovery();
            if (!mmSocket.isConnected()) {
                try {
                    // Establish the connection.  This will block until it connects.
                    mmSocket.connect();
                } catch (final IOException e) {
                    if ((progressDialog != null) && progressDialog.isShowing())
                        progressDialog.dismiss();
                    try {
                        mmSocket.close();
                        ((Activity) context).runOnUiThread(new Runnable() {
                            public void run() {
                                errorExit("Fatal Error", "In ConnectToArduinoBluetoothTask and socket connection failed." + "\n" + e.toString());
                            }
                        });
                    } catch (final IOException e2) {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            public void run() {
                                errorExit("Fatal Error", "In ConnectToArduinoBluetoothTask and socket closing failed during connection failure." + "\n" + e2.toString());
                            }
                        });
                    }
                    // Prevents the onPostExecute from running
                    cancel(true);
                }
            }
            return mmSocket.isConnected();
        }

        protected void onPostExecute(Boolean isArduinoBTDeviceConnected) {
            if ((progressDialog != null) && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (isArduinoBTDeviceConnected) {
                try {
                    mConnectedThread = new ConnectedThread(mmSocket);
                    mConnectedThread.start();
                    // Let the Arduino know to start/resume transmitting feedback data
                    setDataToArduinoResumePausePoll("R");
                    mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);

                    IgnitionSoundHelper.playIgnitionSound(getActivity());
                } catch (IllegalStateException e) {
                    errorExit("Fatal Error", "In ConnectToArduinoBluetoothTask and connected thread start failed." + "\n" + e.toString());
                }
            }
        }
    }

    /**
     * Create new class for connect thread
     */
    protected class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        // Creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                errorExit("Fatal Error", "In ConnectedThread and unable to read/write." + "\n" + e.toString());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] msgBuffer = new byte[256];
            int bytes;

            long tStartLastTransmissionToArduino = System.currentTimeMillis();
            long tStartLastTransmissionFromArduino = System.currentTimeMillis();
            int sendInterval = 1000; // in ms
            int secondsPassedSinceLastTransmissionFromArduino = 0;
            int secondsPassedSinceLastTransmissionToArduino = 0;

            // Keep looping to listen for received messages
            while(!destroyConnectedThread) {
                try {
                    secondsPassedSinceLastTransmissionToArduino = (int) ((System.currentTimeMillis() - tStartLastTransmissionToArduino)/1000);
                    if (secondsPassedSinceLastTransmissionToArduino > 0) {
                        // Note: At the moment, you must write chars because the Arduino hasn't been programmed to
                        // handle Strings
                        if (!pauseConnectedThread) {
                            setDataToArduinoResumePausePoll("*");
                            write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR); // ~1 Second Poll
                        }
                        tStartLastTransmissionToArduino = System.currentTimeMillis();
                    }

                    if (mmInStream.available() > 0) {
                        tStartLastTransmissionFromArduino = System.currentTimeMillis();
                        secondsPassedSinceLastTransmissionFromArduino = 0;
                        synchronized(lock) {
                            // Read bytes from input buffer
                            bytes = mmInStream.read(msgBuffer);
                            String readMessage = new String(msgBuffer, 0, bytes);
                            Log.e("readMessage", readMessage);
                            // Send the obtained bytes to the UI Activity via handler
                            // Note the second and third parameter aren't really used
                            bluetoothDataReceivedHandler.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                            // Will block until mConnectedThread.notify() is called on the main thread
                            // (where mConnectedThread = the object lock
                            // and main thread is another thread compared to the "ConnectedThread").
                            lock.wait();
                        }
                    } else {
                        // If for a second you haven't received any data (and bluetooth hasn't been disconnected),
                        // display the text "Loading..." on screen, along with the amount of seconds that have passed
                        synchronized(lock) {
                            /** Used to show only every second */
                            int tempSecondsPassedSinceLastTransmissionFromArduino = (int) ((System.currentTimeMillis() - tStartLastTransmissionFromArduino)/1000);
                            if (tempSecondsPassedSinceLastTransmissionFromArduino > secondsPassedSinceLastTransmissionFromArduino) {
                                secondsPassedSinceLastTransmissionFromArduino = tempSecondsPassedSinceLastTransmissionFromArduino;
                                if (secondsPassedSinceLastTransmissionFromArduino >= 2) { // Made the threshold 2 seconds to start, instead of 1, to avoid just a minor lapse of communication
                                    // In case there are any characters in the string builder left over from residue (e.g. transmission stops between a '#' and a '0~'...)
                                    receivedDataString.delete(0, receivedDataString.length());
                                    bluetoothDataReceivedHandler.obtainMessage(handlerState, -1, -1,
                                            "Out of Range (Loading..." + Integer.toString(secondsPassedSinceLastTransmissionFromArduino) + ")" + TRANSMISSION_EOL_INDICATOR).sendToTarget();
                                    // Blocks the thread until mConnectedThread.notify() is called on the main thread
                                    // (where mConnectedThread = the object lock
                                    // and main thread is another thread compared to the "ConnectedThread").
                                    lock.wait();
                                    // lock.notify() returns you here (or after the other "wait()" call)

                                    if (secondsPassedSinceLastTransmissionFromArduino >= 10) {
                                        Log.e(">= 10 seconds passed", Integer.toString(secondsPassedSinceLastTransmissionFromArduino));
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    // break;
                } catch (InterruptedException e) { // Catches an InterruptedException from the "wait()" call
                    e.printStackTrace();
                }
            }
        }

        // Write method
        public void write(String message) { //same as the old sendData() method
            byte[] msgBuffer = message.getBytes();
            Log.d(TAG, "...Sending data: " + message + "...");
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                String msg = "In onResume() or onPause() and an exception occurred during write: " + e.getMessage();
                if (MAC_ADDRESS.equals("00:00:00:00:00:00"))
                    msg = msg + ".\n\nUpdate your SPP UUID from 00:00:00:00:00:00 to the correct address";
                // errorExit("Fatal Error", msg);
                // Note the first -1 (and pretty much all of them) are just arbitrary numbers at this point
                errorHandler.obtainMessage(-1, -1, -1, msg).sendToTarget();
            }
        }

        public void closeStreams() {
            try {
                // Don't leave Bluetooth sockets open when leaving the activity
                mmInStream.close();
                mmOutStream.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In ConnectingThread and failed to close sockets." + "\n" + e2.toString());
            }
        }
    }


    private void setDataToArduinoResumePausePoll(String resumePausePoll) {
        switch (resumePausePoll) {
            case "R":
                dataToArduino = resumePausePoll + dataToArduino.substring(1);
                break;
            case "P":
                dataToArduino = resumePausePoll + dataToArduino.substring(1);
                break;
            case "*":
                dataToArduino = resumePausePoll + dataToArduino.substring(1);
                break;
        }
    }

    private void setDataToArduinoLedState(String ledState) {
        switch (ledState) {
            case "0":
                dataToArduino = dataToArduino.substring(0, 1) + ledState + dataToArduino.substring(2);
                break;
            case "1":
                dataToArduino = dataToArduino.substring(0, 1) + ledState + dataToArduino.substring(2);
                break;
        }
    }

    private void setDataToArduinoThrottlePercentage(int percentage) {
        String throttlePercentage = String.format("%03d", percentage); // Left pad with 0's up to 3 chars long        }
        dataToArduino = dataToArduino.substring(0, 2) + throttlePercentage + dataToArduino.substring(5);
    }


    public void setEmergencyBrakesOn() {
        if (btSocket != null && btSocket.isConnected()) {
            if (mConnectedThread != null) {
                // Instead of having another char for emergency brakes, use a special string in the throttlePercentage slot
                // to indicate emergency brakes
                dataToArduino = dataToArduino.substring(0, 2) + DATA_TO_ARDUINO_EMERGENCY_BRAKE_SIGNAL + dataToArduino.substring(5);
                mConnectedThread.write(TRANSMISSION_START_INDICATOR + dataToArduino + TRANSMISSION_EOL_INDICATOR);
            }
        }
        seekbarThrottle.setProgress(0);
    }


    public void setTextOdometer(int kmPerHourSpeed) {
        String speed = Integer.toString(kmPerHourSpeed);
        speed = String.format("%3s", speed);
        textOdometerOnes.setText(Character.toString(speed.charAt(2)));
        textOdometerTens.setText(Character.toString(speed.charAt(1)));
        textOdometerHundreds.setText(Character.toString(speed.charAt(0)));
    }


    /**
     * Quick and dirty way to get the HC-06 Mac Address
     */
    private void getBtMacAddresses(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e("Bluetooth","Not found");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                Log.e("Mac Addressess", ""+mBluetoothAdapter.getRemoteDevice(device.getAddress()));
            }
        }
    }
}
