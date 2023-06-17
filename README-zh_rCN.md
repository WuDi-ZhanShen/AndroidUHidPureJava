# AndroidUHidPureJava
纯Java实现的uhid模拟HID设备！

# How it works
安卓6.0.1起，系统内置了hidcommand_jni库。加载这个库并使用库中的JNI函数即可。\

# How to build this project and how to use
使用Android Studio编译此项目，您会得到一个apk文件。解压apk文件中的classes.dex并将其放置在Android设备中的任意位置，比如/sdcard/classes.dex。随后使用以下命令运行此dex文件：

export CLASSPATH=/sdcard/classes.dex;app_process / com.android.commands.hid.Hid
