package rh.system.shell;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class SafActivity extends Activity {

    private static final int REQUEST_CODE_SAF = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION 
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION 
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, REQUEST_CODE_SAF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SAF && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri treeUri = data.getData();

                try {
                    final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(treeUri, takeFlags);

                    SharedPreferences prefs = getSharedPreferences("TerminalLock", MODE_PRIVATE);
                    prefs.edit().putString("saf_uri", treeUri.toString()).apply();

                    Toast.makeText(this, "Sukses! Akses SD Card Dibuka.", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    Toast.makeText(this, "Gagal ambil izin permanen: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(this, "Dibatalkan.", Toast.LENGTH_SHORT).show();
        }

        finish();
    }
}
