# Electric Skateboard
Android and Arduino Code for Controlling an Electric Skateboard

Prerequisites: Android and Arduino

Hardware:
- Arduino Atmega2560
- HC-06 Bluetooth Module
- Galaxy S6 Android 7.0 (A modern android phone should suffice)

How to simply use the app and set things up:
1. Connect the Arduino to your computer and upload the Arduino code to the Atmega2560.
2. Connect the Bluetooth module to the Arduino by wiring the following:

| HC-06 | Atmega2560 |
| --- | --- |
| `RX` | `TX3` (Pin 14) |
| `TX` | `RX3` (Pin 15) |
| `GND` | `GND` |
| `VCC` | `5V` |

![picture alt](https://github.com/graduit/e-skate/blob/master/Arduino/Arduino%20HC-06.jpg "HC-06")

Figure 1 - HC-06

![picture alt](https://github.com/graduit/e-skate/blob/master/Arduino/Arduino%20Atmega2560.jpg "Atmega2560")

Figure 2 - Atmega2560

3. Pair the Android device to the HC-06 Bluetooth Module by turning on your phones Bluetooth and connecting to the module. Pairing may ask for a pin: Pin is usually 0000 or 1234. Once the devices are paired, make sure it is the only device currently paired.
4. Next, upload the android code to your phone. This will crash the first time round, which is expected. Check the android monitor for an error log “Mac Addressess: “. Get the address which should look like “00:00:00:00:00” and replace the address in the “Android>MainActivityFragment.java” file. Next, comment out or delete the line “getMacAddresses();” in the same file. Run the code again, and it should connect.
5. Finally, disconnect the Arduino from the computer and wire up the following*:

| ESC | Atmega2560 |
| --- | --- |
| `5V` | `5V` |
| `GND` | `GND` |
| `Signal` | `Pin 9` |

*Note it is not a good idea to power your Arduino with the computer USB and the esc simultaneously. These connections are in addition to the connections to the Bluetooth module.

6. Use the slider to control the speed.
7. Be safe, have fun!

Current features:
- Use the slider on screen to control speed.
- Use the volume buttons to control the slider, and in turn, control speed.
- Hold the phone with your left hand in a relaxed position. Flick the phone forward and back twice to increase speed, or flick the phone back and forward twice to decrease speed.
- Turn the phones LED On/Off with the corresponding buttons.
- Press the pink button for emergency brakes.
- Press the yellow button for voice recognition: “speed up” returns the audio “Speeding up”, and “slow down” returns the audio “Slowing down”. (Note I was playing around with this but it is not a feasible solution in its current state)

Future work:
- Use a service for the Bluetooth connection.
- Voice recognition - was laggy (need realtime) + would force a disconnect in current state
- Integrate search for Bluetooth device and allow for a selection instead of hardcoding mac address
