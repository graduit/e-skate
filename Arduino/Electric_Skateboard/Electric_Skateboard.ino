#include <Servo.h>

/**
 * dataFromAndroid[0] - "P" = Paused, "R" = Resumed
 * dataFromAndroid[1] - "0" = LED off, "1" = LED on
 * dataFromAndroid[2-4] - Percentage of throttle , 0 to 100 (ZZZ for emergency brakes)
 */
String dataFromAndroid = "P0000";
int currentThrottlePercentage = 0;

// Used to hold the LED (Pin 13) on/off state (for an Atmega2560)
String ledStatus = "0";

String motorSpeed = "000"; // km/h

unsigned long previousMillisDataSent = 0UL; // Will store the last time data was sent to Android (UL => Unsigned Long)
unsigned long previousMillisDataReceived = 0UL; // Will store the last time data was received from Android (UL => Unsigned Long)
const long SEND_RATE = 1000; // Interval at which to send data to android (milliseconds)
const long DISCONNECTED_THRESHOLD = 3000; // Time after which to act disconnected (milliseconds)
boolean arduinoBTpreviouslyConnected = false;

unsigned long previousMillisPauseReceived = 0UL; // Will store the last time pause data was received from Android (UL => Unsigned Long)

const String TRANSMISSION_START_INDICATOR = "#"; // Prepend the data we send with TRANSMISSION_START_INDICATOR to indicate start of data
const String TRANSMISSION_EOL_INDICATOR = "~"; // Append the data we send with TRANSMISSION_EOL_INDICATOR to indicate end of data (EOL => End Of Line)

Servo esc;
const String EMERGENCY_BRAKE_SIGNAL = "ZZZ"; // dataFromAndroid[2-4]

unsigned long previousMillisThrottlePercentageChanged = 0UL; // Will store the last time the throttle percentage was changed
const long THROTTLE_PERCENTAGE_CHANGE_INTERVAL = 0; // Update the throttle percentage every 0 ms

boolean isPoweredDown = false; // is powered down, and out of sync
const String DATA_FROM_ANDROID_REQUIRED_FOR_RESET_WHEN_ARDUINO_IS_POWERED_DOWN = "R0000"; // i.e. when the Arduino is in the powered down state, need to resync with Android
const String DATA_TO_ANDROID_WHEN_ARDUINO_POWERED_DOWN = "Arduino Powered Down";

void setup() {
  Serial.begin(9600);
  Serial.println("Electric Skateboard Arduino Code Starting...");
  
  // The data rate for the Serial port needs to match the data rate for your bluetooth board.
  Serial3.begin(9600);
  
  pinMode(13, OUTPUT);  
  digitalWrite(13, LOW); // Set LED off

  esc.attach(9, 1000, 2000); // Attaches the servo (or the brushless outrunner motor in this case) on pin 9 to the servo object

  esc.write(0);
  delay(5000); // Initialise the ESC with a low signal for about 5 seconds
}

void loop() {
  // Returns the number of milliseconds since the Arduino board began running the current program.
  unsigned long currentMillis = millis();
  
  if (Serial3.available()) {
    char charFromAndroid = Serial3.read();
    previousMillisDataReceived = currentMillis;
    Serial.println(charFromAndroid);
    
    if ((String) charFromAndroid == TRANSMISSION_START_INDICATOR) {
      dataFromAndroid = "";
      return;
    } else if ((String) charFromAndroid != TRANSMISSION_EOL_INDICATOR) {
      dataFromAndroid = dataFromAndroid + charFromAndroid;
      return;
    }
    // If it reaches here, you have received your data starting with TRANSMISSION_START_INDICATOR and ending with TRANSMISSION_EOL_INDICATOR

    if (dataFromAndroid == DATA_FROM_ANDROID_REQUIRED_FOR_RESET_WHEN_ARDUINO_IS_POWERED_DOWN) {
      isPoweredDown = false;
    }
    if (isPoweredDown) {
      sendArduinoPoweredDownFlagToAndroid();
      return;
    }

    if (dataFromAndroid[0] == 'R') { // Should start with an 'R'
        if (arduinoBTpreviouslyConnected == false) {
          arduinoBTpreviouslyConnected = true; // Prevents count start until the first char has been read in (i.e. it's connected)
        }
    }

    if (arduinoBTpreviouslyConnected == true && dataFromAndroid[0] != 'P') {
      // Just give Android immediate feedback when it polls to let it know Arduino is still connected
/*      if (dataFromAndroid != "*") // Don't print for the poll case
        Serial.println(dataFromAndroid); */
      sendDataToAndroid();
      previousMillisDataSent = currentMillis; // Start the count
    } else if (dataFromAndroid[0] == 'P') {
      Serial.println(dataFromAndroid[0]);
      previousMillisPauseReceived = currentMillis; // Start the count
    }
  }

  if (isPoweredDown) {
    return;
  }

  if (dataFromAndroid.length() == DATA_FROM_ANDROID_REQUIRED_FOR_RESET_WHEN_ARDUINO_IS_POWERED_DOWN.length()) {
    if (dataFromAndroid[1] == '0') {
      digitalWrite(13, LOW); // Turn off LED
      ledStatus = "0";
    } else if (dataFromAndroid[1] == '1') {
      digitalWrite(13, HIGH); // Turn on LED
      ledStatus = "1";
    }  
    setEscOutput(currentMillis);
  }

  if (!Serial3.available()) {    
    // If paused nothing happens
    if (dataFromAndroid[0] == 'P') {
      if (arduinoBTpreviouslyConnected == true) {
        // Should run every second after a Pause signal has been received from Android
        if (((currentMillis - previousMillisPauseReceived)/1000) > 0) {
          Serial.println(dataFromAndroid[0]);
          previousMillisPauseReceived = currentMillis;
        }
      }
    } else if (dataFromAndroid[0] == 'R') {    
      if (currentMillis - previousMillisDataSent >= SEND_RATE) { // Always true the first time since previousMillisDataSent initalized to 0
        sendDataToAndroid();
        Serial.println(dataFromAndroid[0]);
        previousMillisDataSent = currentMillis; // Reset the count
      }
    }
  
    if (arduinoBTpreviouslyConnected == true) {
      if ((currentMillis - previousMillisDataReceived) >= DISCONNECTED_THRESHOLD) {
        Serial.println("Most likely disconnected");
        // Power down
        digitalWrite(13, LOW);
        ledStatus = "0";
        dataFromAndroid[1] = '0';
        
        // Set to 'P' such that if the Android app is paused, Arduino will stop transmitting data.
        // Essentially at this point it "auto locks", then since the only way to unlock it is being connected
        // i.e. having an 'R' come though
        dataFromAndroid[0] = 'P';                          
        arduinoBTpreviouslyConnected = false;    

        dataFromAndroid[2] = '0';
        dataFromAndroid[3] = '0';
        dataFromAndroid[4] = '0';
        currentThrottlePercentage = 0;
        setEscOutput(currentMillis);

        isPoweredDown = true;
      } else if (currentMillis < previousMillisDataReceived) {
        /* I.e the unsigned long has overflowed back to 0 (after approximately 50 days...)
           Can assume this is never a use case...
           previousMillisDataReceived = 0;
           TODO: Would need to handle the similar case for previousMillisDataSent... */
      }
    }
  }
}

// TODO make this a method with an input for data to send to Android
// Sends the values over serial to BT module
void sendDataToAndroid() {
  Serial3.print(TRANSMISSION_START_INDICATOR);
  Serial3.print(ledStatus + motorSpeed);
  Serial3.print(TRANSMISSION_EOL_INDICATOR);
  
  //Serial3.print(TRANSMISSION_START_INDICATOR + ((String) dataFromAndroid) + TRANSMISSION_EOL_INDICATOR);
  delay(10); // Added a delay to eliminate missed transmissions (in ms)
}

// Send a special message to Android to indicate the Arduino is in a "PoweredDown" state and needs to handle that protocol
void sendArduinoPoweredDownFlagToAndroid() {
  Serial3.print(TRANSMISSION_START_INDICATOR + DATA_TO_ANDROID_WHEN_ARDUINO_POWERED_DOWN + TRANSMISSION_EOL_INDICATOR);
  delay(10);
}

void setEscOutput(unsigned long currentMillis) {
  if (dataFromAndroid.substring(2,5) == EMERGENCY_BRAKE_SIGNAL) {
    esc.write(90);
    dataFromAndroid = dataFromAndroid.substring(0, 2) + "000";
    currentThrottlePercentage = 0;
    previousMillisThrottlePercentageChanged = currentMillis;
    return;
  }
  
  // A value from 0 to 100 which indicates the min or max percentage of Wide Open Throttle (WOD)
  int throttlePercentage = dataFromAndroid.substring(2,5).toInt();

  if (currentMillis - previousMillisThrottlePercentageChanged > THROTTLE_PERCENTAGE_CHANGE_INTERVAL) {
    if (throttlePercentage > currentThrottlePercentage) {
      currentThrottlePercentage++;
      int escOutput = map(currentThrottlePercentage,0,100,90,180); // Maps 0-100% throttle to 90-180 (i.e. no speed to full speed in one direction)
      esc.write(escOutput);
      previousMillisThrottlePercentageChanged = currentMillis;
    } else if (throttlePercentage < currentThrottlePercentage) {
      currentThrottlePercentage--;
      int escOutput = map(currentThrottlePercentage,0,100,90,180);
      esc.write(escOutput);
      previousMillisThrottlePercentageChanged = currentMillis;
    }
  }

  // Mainly for when "Powered Down" state is triggered
  if (throttlePercentage == 0 && currentThrottlePercentage == 0) {
    esc.write(90);
  }
}
