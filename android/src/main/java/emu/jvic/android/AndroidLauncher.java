package emu.jvic.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import emu.jvic.JVic;

/** Launches the Android application. */
public class AndroidLauncher extends AndroidApplication {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true; // Recommended, but not required.
        initialize(new JVic(), configuration);
    }

    @Override
    public void confirm(final String message, final ConfirmResponseHandler responseHandler) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(AndroidLauncher.this).setTitle("Please confirm").setMessage(message)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                responseHandler.yes();
                                dialog.cancel();
                            }
                        }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                responseHandler.no();
                                dialog.cancel();
                            }
                        }).create().show();
            }
        });
    }
}