package emu.jvic.teavm;

import com.badlogic.gdx.Gdx;

import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.OpenFileResponseHandler;
import emu.jvic.ui.TextInputResponseHandler;

public class TeaVMDialogHandler implements DialogHandler {

    private boolean dialogOpen;

    @Override
    public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
        Gdx.app.postRunnable(() -> {
            dialogOpen = true;
            if (TeaVMBrowser.confirm(message)) {
                confirmResponseHandler.yes();
            } else {
                confirmResponseHandler.no();
            }
            dialogOpen = false;
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
            String text = TeaVMBrowser.prompt(message, initialValue);
            if (text != null) {
                textInputResponseHandler.inputTextResult(true, text);
            } else {
                textInputResponseHandler.inputTextResult(false, null);
            }
            dialogOpen = false;
        });
    }

    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(() -> {
            dialogOpen = true;
            TeaVMBrowser.alert(aboutMessage);
            textInputResponseHandler.inputTextResult(true, "OK");
            dialogOpen = false;
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