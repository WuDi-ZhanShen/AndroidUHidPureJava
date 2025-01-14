# AndroidUHidPureJava
纯Java实现的uhid模拟HID设备！

# How it works
安卓6.0.1起，系统内置了hidcommand_jni库。加载这个库并使用库中的JNI函数即可。

# How to to use
打开APP时会自动解压classes.dex至/sdcard/Android/data/uhid.purejava/files/classes.dex。用此命令即可启动uhid模拟：

export CLASSPATH=/sdcard/Android/data/uhid.purejava/files/classes.dex;app_process / com.android.commands.hid.Hid
