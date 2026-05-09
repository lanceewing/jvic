package emu.jvic.teavm;

import com.badlogic.gdx.Gdx;
import org.teavm.jso.JSObject;

import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.OpenFileResponseHandler;
import emu.jvic.ui.TextInputResponseHandler;

public class TeaVMDialogHandler implements DialogHandler {

    private boolean dialogOpen;
    private final JSObject dialog = TeaVMBrowser.getDialogInstance();

    @Override
    public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
        Gdx.app.postRunnable(() -> {
            dialogOpen = true;
            TeaVMBrowser.showDialogConfirm(dialog, message, confirmed -> {
                if (confirmed) {
                    confirmResponseHandler.yes();
                } else {
                    confirmResponseHandler.no();
                }
                dialogOpen = false;
            });
        });
    }

    @Override
    public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler) {
        dialogOpen = true;
        TeaVMBrowser.openFileDialog((success, fileName, binaryData) -> {
            if (success && (fileName != null) && (binaryData != null)) {
                openFileResponseHandler.openFileResult(true, fileName, convertBinaryStringToBytes(binaryData));
            } else {
                openFileResponseHandler.openFileResult(false, null, null);
            }
            dialogOpen = false;
        });
    }

    @Override
    public void promptForTextInput(String message, String initialValue,
            TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(() -> {
            dialogOpen = true;
            TeaVMBrowser.showDialogPrompt(dialog, message, initialValue, (accepted, value) -> {
                if (accepted) {
                    textInputResponseHandler.inputTextResult(true, value);
                } else {
                    textInputResponseHandler.inputTextResult(false, null);
                }
                dialogOpen = false;
            });
        });
    }

    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(() -> {
            dialogOpen = true;
            TeaVMBrowser.showDialogAlert(dialog, aboutMessage, (accepted, value) -> {
                if (accepted) {
                    textInputResponseHandler.inputTextResult(true, value);
                } else {
                    textInputResponseHandler.inputTextResult(false, null);
                }
                dialogOpen = false;
            });
        });
    }

    private byte[] convertBinaryStringToBytes(String binaryStr) {
        byte[] bytes = new byte[binaryStr.length()];
        for (int i = 0; i < binaryStr.length(); i++) {
            bytes[i] = (byte)(binaryStr.charAt(i) & 0xFF);
        }
        return bytes;
    }

    @Override
    public boolean isDialogOpen() {
        return dialogOpen;
    }
}