# AndroidUHidPureJava

A HID device simulation implemented purely in Java! 

# How it works

Starting from Android 6.0.1, the "hidcommand_jni" library is built into the system. We can load this library and use the JNI functions provided in it.

# How to use

Once you open the app, it will automaticly unzip the classes.dex from its apk file to /sdcard/Android/data/uhid/purejava/files/classes.dex. Then you can use this command to launch uhid simulation:

export CLASSPATH=/sdcard/Android/data/uhid/purejava/files/classes.dex;app_process / com.android.commands.hid.Hid

Adb or Root permission is required.

Note: you can also manually unzip classes.dex from apk file. Just remember to change the filePath parameter in the launch command.

# Support range

Android 6.0.1 ~ Android 15
