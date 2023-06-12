package com.android.commands.hid;

import java.util.Random;
import java.util.Scanner;

//usage: export CLASSPATH=/sdcard/classes.dex;app_process / com.android.commands.hid.Hid

public class Hid {

    //This is a normal hid keyboard's descriptor.
    private static final byte[] hidKeyboardDescriptor = new byte[]{

            0x05, 0x01,        // Usage Page (Generic Desktop Ctrls)
            0x09, 0x06,        // Usage (Keyboard)
            (byte) 0xA1, 0x01,        // Collection (Application)
            0x05, 0x07,        //   Usage Page (Kbrd/Keypad)
            0x19, (byte) 0xE0,        //   Usage Minimum (0xE0)
            0x29, (byte) 0xE7,        //   Usage Maximum (0xE7)
            0x15, 0x00,        //   Logical Minimum (0)
            0x25, 0x01,        //   Logical Maximum (1)
            0x75, 0x01,        //   Report Size (1)
            (byte) 0x95, 0x08,        //   Report Count (8)
            (byte) 0x81, 0x02,        //   Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
            (byte) 0x95, 0x01,        //   Report Count (1)
            0x75, 0x08,        //   Report Size (8)
            (byte) 0x81, 0x03,        //   Input (Const,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
            (byte) 0x95, 0x05,        //   Report Count (5)
            0x75, 0x01,        //   Report Size (1)
            0x05, 0x08,        //   Usage Page (LEDs)
            0x19, 0x01,        //   Usage Minimum (Num Lock)
            0x29, 0x05,        //   Usage Maximum (Kana)
            (byte) 0x91, 0x02,        //   Output (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
            (byte) 0x95, 0x01,        //   Report Count (1)
            0x75, 0x03,        //   Report Size (3)
            (byte) 0x91, 0x03,        //   Output (Const,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
            (byte) 0x95, 0x06,        //   Report Count (6)
            0x75, 0x08,        //   Report Size (8)
            0x15, 0x00,        //   Logical Minimum (0)
            0x25, 0x65,        //   Logical Maximum (101)
            0x05, 0x07,        //   Usage Page (Kbrd/Keypad)
            0x19, 0x00,        //   Usage Minimum (0x00)
            0x29, 0x65,        //   Usage Maximum (0x65)
            (byte) 0x81, 0x00,        //   Input (Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
            (byte) 0xC0,              // End Collection

    };


    //This is a normal hid mouse's descriptor.
    private static final byte[] hidMouseDescriptor = new byte[]{

            0x05, 0x01,        // Usage Page (Generic Desktop Ctrls)
            0x09, 0x02,        // Usage (Mouse)
            (byte) 0xA1, 0x01,        // Collection (Application)
            0x09, 0x01,        //   Usage (Pointer)
            (byte) 0xA1, 0x00,        //   Collection (Physical)
            0x05, 0x09,        //     Usage Page (Button)
            0x19, 0x01,        //     Usage Minimum (0x01)
            0x29, 0x05,        //     Usage Maximum (0x05)
            0x15, 0x00,        //     Logical Minimum (0)
            0x25, 0x01,        //     Logical Maximum (1)
            (byte) 0x95, 0x05,        //     Report Count (5)
            0x75, 0x01,        //     Report Size (1)
            (byte) 0x81, 0x02,        //     Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
            (byte) 0x95, 0x01,        //     Report Count (1)
            0x75, 0x03,        //     Report Size (3)
            (byte) 0x81, 0x01,        //     Input (Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
            0x05, 0x01,        //     Usage Page (Generic Desktop Ctrls)
            0x09, 0x30,        //     Usage (X)
            0x09, 0x31,        //     Usage (Y)
            0x09, 0x38,        //     Usage (Wheel)
            0x15, (byte) 0x81,        //     Logical Minimum (-127)
            0x25, 0x7F,        //     Logical Maximum (127)
            0x75, 0x08,        //     Report Size (8)
            (byte) 0x95, 0x03,        //     Report Count (3)
            (byte) 0x81, 0x06,        //     Input (Data,Var,Rel,No Wrap,Linear,Preferred State,No Null Position)
            (byte) 0xC0,              //   End Collection
            (byte) 0xC0,              // End Collection

    };


    // The keyboardCode array is 8 bytes long and represents the report data for a hid keyboard.
    // keyboardCode[0] represents the MetaKeys' states of the keyboard (Such as "Win", "Ctrl", "Shift", "Alt"). Assigned by using the formula: keyboardCode[0] |= 1; keyboardCode[0] &= ~1;
    // keyboardCode[1] represents nothing and is not used.
    // keyboardCode[2] to keyboardCode[7] each represent a normal key pressed on the keyboard.
    public static byte[] keyboardCode = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};


    // The mouseCode array is 4 bytes long and represents the report data for a hid mouse.
    // mouseCode[0] represents the buttons' states of the mouse. Assigned by using the formula: mouseCode[0] |= 1; mouseCode[0] &= ~1;
    // mouseCode[1] represents the X axis value of the mouse. Assigned normally.
    // mouseCode[2] represents the Y axis value of the mouse. Assigned normally.
    // mouseCode[3] represents the SCROLL axis value of the mouse. Assigned by using the formula: mouseCode[3] = (byte) (~scrollValue + 1);
    public static byte[] mouseCode = {0x00, 0x00, 0x00, 0x00};

    public static void main(String[] args) {

        //Check permission.
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) {
            System.err.printf("Insufficient permission! Need to be launched by adb (uid 2000) or root (uid 0), but your uid is %d \n", uid);
            System.exit(255);
            return;
        }


        // We create two uHid devices, one for the mouse and one for the keyboard.
        // The id, name, vid, pid, and bus parameters do not matter. Only the descriptor parameter matters.

        Device mouse = new Device(1, "uHidMouse", 1234, 5678, 0x03, hidMouseDescriptor, mouseCode, null, null);
        Device keyboard = new Device(2, "uHidKeyboard", 8765, 4321, 0x03, hidKeyboardDescriptor, keyboardCode, null, null);


        System.out.println("");
        System.out.println("Create uhid mouse and uhid keyboard successfully.");
        System.out.println("Enter \"m\" to send random mouse move;");
        System.out.println("Enter \"k\" to send random keyboard keyPress or keyRelease;");
        System.out.println("Enter \"exit\" to exit.");
        System.out.println("");


        // The Scanner is used here only for testing purposes, it allows easy user input to simulate mouse and keyboard events.
        // A Random object is created to generate random codes for the mouse and keyboard events.
        // The program enters a label loop that reads input from the Scanner until the user types "exit".
        // Depending on the user input, the program generates and sends a report for the mouse or keyboard event using the corresponding sendReport() method.

        Scanner scanner = new Scanner(System.in);
        String inline;
        Random random = new Random();
        label:
        while ((inline = scanner.nextLine()) != null) {
            switch (inline) {

                case "exit":
                    break label;

                case "m":
                    mouseCode[1] = (byte) random.nextInt();
                    mouseCode[2] = (byte) random.nextInt();
                    mouse.sendReport(mouseCode);//Here we send mouse report data.
                    break;

                case "k":
                    int randomInt = random.nextInt();
                    keyboardCode[2] = randomInt % 2 == 0 ? (byte) randomInt : 0;
                    keyboard.sendReport(keyboardCode);//Here we send keyboard report data.
                    break;
            }
        }
        scanner.close();
        System.out.println("Bye!");

        // Close the uhid devices.
        // But seems that the uhid devices will be closed automatically at the end of this program, so manually closing is not necessary.
        mouse.close();
        keyboard.close();
    }


}
