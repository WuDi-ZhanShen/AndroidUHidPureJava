# AndroidUHidPureJava
纯Java实现的uhid模拟HID设备！



A HID device simulation implemented purely in Java!

# How it works
安卓6.0.1起，系统内置了hidcommand_jni库。加载这个库并使用库中的JNI函数即可。但请注意，这个库中的JNI函数的接收参数在安卓8.1.0之后有变化，而本项目中的Java代码对应于安卓8.1.0后的JNI函数。因此，本项目仅能在安卓8.1.0及以上使用。如果您想在安卓6.0.1和安卓8.0.0之间使用hidcommand_jni库，请按AOSP中的 http://www.aospxref.com/android-6.0.1_r9/xref/frameworks/base/cmds/hid/src/com/android/commands/hid/Device.java 修改本项目中的JNI函数的接收参数。




Starting from Android 6.0.1, the hidcommand_jni library is built into the system. We can load this library and use the JNI functions provided in it. However, please note that the input parameters of the JNI functions in this library have changed since Android 8.1.0. Therefore, the Java code in this project corresponds to the JNI functions after Android 8.1.0, and this project can only be used on Android 8.1.0 and above. If you want to use the hidcommand_jni library between Android 6.0.1 and Android 8.0.0, please modify the input parameters of the JNI functions in this project according to the AOSP at http://www.aospxref.com/android-6.0.1_r9/xref/frameworks/base/cmds/hid/src/com/android/commands/hid/Device.java .

# How to build this project
使用Android Studio编译此项目，您会得到一个apk文件。解压apk文件中的classes.dex并将其放置在Android设备中的任意位置，比如/sdcard/classes.dex。随后使用以下命令运行此dex文件：

export CLASSPATH=/sdcard/classes.dex;app_process / com.android.commands.hid.Hid



By compiling this project with Android Studio, you will get an apk file. Unzip the classes.dex file from the apk and place it anywhere on your Android device, such as /sdcard/classes.dex. Then, use the following command to run this dex file:

export CLASSPATH=/sdcard/classes.dex;app_process / com.android.commands.hid.Hid
