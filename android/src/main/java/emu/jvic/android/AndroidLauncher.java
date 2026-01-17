package emu.jvic.android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.hbisoft.pickit.PickiT;
import com.hbisoft.pickit.PickiTCallbacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import emu.jvic.JVic;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.OpenFileResponseHandler;
import emu.jvic.ui.TextInputResponseHandler;

/** Launches the Android application. */
public class AndroidLauncher extends AndroidApplication implements DialogHandler, PickiTCallbacks {
    
    private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = 23;

    private PickiT pickiT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        Map<String, String> argsMap = new HashMap<>();
        AndroidJVicRunner androidJVicRunner = new AndroidJVicRunner(
                new AndroidKeyboardMatrix(), new AndroidPixelData(),
                new AndroidSoundGenerator()
        );
        initialize(new JVic(androidJVicRunner, this, argsMap), configuration);
        pickiT = new PickiT(this, this, this);
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

    @Override
    public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler) {
        activeOpenFileResponseHandler = openFileResponseHandler;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chooseFileWrapper();
            }
        });
    }

    private void chooseFileWrapper() {
        int hasWriteExternalStoragePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                },
                PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE);
            return;
        }
        chooseFile();
    }

    private void chooseFile() {
        Intent intent = new Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra("return-data", true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(
                Intent.createChooser(intent, "Select a file"), 6384);
    }

    // TODO: Has to be a nicer way than using an instance var for this. Can we create a separate Activity rather than using the main one?
    private OpenFileResponseHandler activeOpenFileResponseHandler;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 6384) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    try {
                        // Get the file path from the URI
                        pickiT.getPath(data.getData(), Build.VERSION.SDK_INT);
                    } catch (Exception e) {
                        Log.e("FileSelectorTestActivity", "File select error", e);
                        activeOpenFileResponseHandler.openFileResult(false, null, null);
                    }
                }
            } else {
                activeOpenFileResponseHandler.openFileResult(false, null, null);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseFile();
                } else {
                    activeOpenFileResponseHandler.openFileResult(false, null, null);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void PickiTonUriReturned() {
    }

    @Override
    public void PickiTonStartListener() {
}

    @Override
    public void PickiTonProgressUpdate(int progress) {
    }

    @Override
    public void PickiTonCompleteListener(String path, boolean wasDriveFile, boolean wasUnknownProvider, boolean wasSuccessful, String Reason) {
        if (wasSuccessful) {
            activeOpenFileResponseHandler.openFileResult(true, path, null);
        } else {
            activeOpenFileResponseHandler.openFileResult(false, null, null);
        }
    }

    @Override
    public void PickiTonMultipleCompleteListener(ArrayList<String> paths, boolean wasSuccessful, String Reason) {
    }

    @Override
    public void promptForTextInput(final String message, final String initialValue,
            final TextInputResponseHandler textInputResponseHandler) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final EditText inputText = new EditText(AndroidLauncher.this);
                inputText.setText(initialValue != null ? initialValue : "");

                // Set the default text to a link of the Queen
                inputText.setHint("");

                new AlertDialog.Builder(AndroidLauncher.this).setTitle("Please enter value").setMessage(message)
                        .setView(inputText).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String text = inputText.getText().toString();
                                textInputResponseHandler.inputTextResult(true, text);
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                textInputResponseHandler.inputTextResult(false, null);
                            }
                        }).show();
            }
        });
    }

    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(AndroidLauncher.this).setTitle("About JVic").setMessage(aboutMessage)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                textInputResponseHandler.inputTextResult(true, "OK");
                                dialog.cancel();
                            }
                        }).create().show();
            }
        });
    }

    @Override
    public boolean isDialogOpen() {
        // Not required for Android, so simply return false regardless of state.
        return false;
    }
}