# AndroidUHidPureJava

A HID device simulation implemented purely in Java! 

# How it works

Starting from Android 6.0.1, the "hidcommand_jni" library is built into the system. We can load this library and use the JNI functions provided in it.

# How to build this project and how to use

By compiling this project with Android Studio, you will get an apk file. Unzip the classes.dex file from the apk and place it anywhere on your Android device, such as /sdcard/classes.dex. Then, use the following command to run this dex file:

export CLASSPATH=/sdcard/classes.dex;app_process / com.android.commands.hid.Hid

Adb or Root permission is required.
