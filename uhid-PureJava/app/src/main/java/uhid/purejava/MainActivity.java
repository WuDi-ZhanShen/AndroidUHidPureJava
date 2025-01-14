package uhid.purejava;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.commands.hid.Hid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setText("Uhid simulation command has been copied. Run it by adb or root.");
        textView.setTextColor(Color.WHITE);
        textView.setGravity(Gravity.CENTER);
        textView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(textView);


        File outputDir = getExternalFilesDir(null);
        boolean success = extractDexFile(outputDir);
        if (success) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String cmd = "export CLASSPATH=" + outputDir.getAbsolutePath() + "/classes.dex;app_process /system/bin " + Hid.class.getName();
            // 创建剪切板内容
            ClipData clip = ClipData.newPlainText("label", cmd);

            // 将内容设置到剪切板
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "command copied to clipboard:\n"+cmd, Toast.LENGTH_SHORT).show();
        }
    }

    public boolean extractDexFile(File outputDir) {
        String apkPath = getPackageCodePath(); // 获取自身 APK 文件路径
        File dexFile = new File(outputDir, "classes.dex");

        // 确保输出目录存在
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            return false;
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry zipEntry;
            // 遍历 ZIP 文件（即 APK），寻找 classes.dex 文件
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if ("classes.dex".equals(zipEntry.getName())) {
                    // 创建目标文件输出流
                    try (OutputStream outputStream = new FileOutputStream(dexFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, len);
                        }
                        zipInputStream.closeEntry();
                        return true;  // 成功解压
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;  // 解压失败
    }
}
