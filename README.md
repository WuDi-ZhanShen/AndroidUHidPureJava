[# AndroidUHidPureJava

A HID device simulation implemented purely in Java!

# How it works

Starting from Android 6.0.1, the "hidcommand_jni" library is built into the system. We can load this library and use the JNI functions provided in it. However, please note that the input parameters of the JNI functions in this library have changed since Android 8.1.0. Therefore, the Java code in this project corresponds to the JNI functions after Android 8.1.0, and this project can only be used on Android 8.1.0 and above. If you want to use the "hidcommand_jni" library between Android 6.0.1 and Android 8.0.0, please modify the input parameters of the JNI functions in this project according to the Android source code at http://www.aospxref.com/android-6.0.1_r9/xref/frameworks/base/cmds/hid/src/com/android/commands/hid/Device.java .

# How to build this project and how to use

By compiling this project with Android Studio, you will get an apk file. Unzip the classes.dex file from the apk and place it anywhere on your Android device, such as /sdcard/classes.dex. Then, use the following command to run this dex file:

export CLASSPATH=/sdcard/classes.dex;app_process / com.android.commands.hid.Hid
