package uhid.purejava;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String apk = getPackageCodePath();
        String command = "CLASSPATH='" + apk
                + "' app_process /system/bin uhid.purejava.Hid";
        String text = "Pure Java UHID\n\n"
                + "The command has been copied to the clipboard.";

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setPadding(48, 48, 48, 48);
        view.setTextIsSelectable(true);
        view.setText(text);
        setContentView(view);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Pure Java UHID command", command));
        Toast.makeText(this, "app_process command copied", Toast.LENGTH_SHORT).show();
    }
}
