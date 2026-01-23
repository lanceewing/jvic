package emu.jvic.gwt;

import com.badlogic.gdx.Gdx;
import com.google.gwt.typedarrays.shared.Int8Array;
import com.google.gwt.typedarrays.shared.TypedArrays;

import emu.jvic.ui.ConfirmResponseHandler;
import emu.jvic.ui.DialogHandler;
import emu.jvic.ui.OpenFileResponseHandler;
import emu.jvic.ui.TextInputResponseHandler;

/**
 * The GWT implementation of the DialogHandler interface.
 */
public class GwtDialogHandler implements DialogHandler {

    private boolean dialogOpen;
    
    /**
     * Constructor for GwtDialogHandler.
     */
    public GwtDialogHandler() {
        initDialog();
    }
    
    private final native void initDialog()/*-{
        this.dialog = new $wnd.Dialog();
    }-*/;
    
    @Override
    public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                showHtmlConfirmBox(message, confirmResponseHandler);
            }
        });
    }

    private final native void showHtmlConfirmBox(String message, ConfirmResponseHandler confirmResponseHandler)/*-{
        var that = this;
        this.dialog.confirm(message).then(function (res) {
            if (res) {
                confirmResponseHandler.@emu.jvic.ui.ConfirmResponseHandler::yes()();
            } else {
                confirmResponseHandler.@emu.jvic.ui.ConfirmResponseHandler::no()();
            }
            that.@emu.jvic.gwt.GwtDialogHandler::dialogOpen = false;
        });
    }-*/;
    
    @Override
    public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler) {
        dialogOpen = true;
        showHtmlOpenFileDialog(new GwtOpenFileResultsHandler() {
            @Override
            public void onFileResultsReady(GwtOpenFileResult[] openFileResultArray) {
                // There should be only one file.
                if (openFileResultArray.length == 1) {
                    GwtOpenFileResult result = openFileResultArray[0];
                    Int8Array fileDataInt8Array = TypedArrays.createInt8Array(result.getFileData());
                    byte[] fileByteArray = new byte[fileDataInt8Array.byteLength()];
                    for (int index=0; index<fileDataInt8Array.byteLength(); index++) {
                        fileByteArray[index] = fileDataInt8Array.get(index);
                    }
                    openFileResponseHandler.openFileResult(true, result.getFileName(), fileByteArray);
                } else {
                     // No files selected.
                    openFileResponseHandler.openFileResult(false, null, null);
                }
                
                dialogOpen = false;
            }
        });
    }
    
    private final native void showHtmlOpenFileDialog(GwtOpenFileResultsHandler resultsHandler)/*-{
        var fileInputElem = document.createElement('input');
        fileInputElem.type = 'file';
        fileInputElem.accept = '.d64,.prg,.crt,.tap,.zip';
        
        document.body.appendChild(fileInputElem);
        
        // The change event occurs after a file is chosen.
        fileInputElem.addEventListener("change", function(event) {
            document.body.removeChild(fileInputElem);
        
            if (this.files.length === 0) {
                // No file was selected, so nothing more to do.
                resultsHandler.@emu.jvic.gwt.GwtOpenFileResultsHandler::onFileResultsReady([Lemu/jvic/gwt/GwtOpenFileResult;)([]);
            }
            else {
                // We do not allow multiple files to be selected.
                Promise.all([].map.call(this.files, function (file) {
                    return new Promise(function (resolve, reject) {
                        var reader = new FileReader();
                        // NOTE 1: loadend called regards of whether it was successful or not.
                        // NOTE 2: file has .name, .size and .lastModified fields.
                        reader.addEventListener("loadend", function (event) {
                            resolve({
                                fileName: file.name,
                                filePath: file.webkitRelativePath? file.webkitRelativePath : '',
                                fileData: reader.result
                            });
                        });
                        reader.readAsArrayBuffer(file);
                    });
                })).then(function (results) {
                    // The results param is an array of result objects
                    resultsHandler.@emu.jvic.gwt.GwtOpenFileResultsHandler::onFileResultsReady([Lemu/jvic/gwt/GwtOpenFileResult;)(results);
                });
            }
        });
        
        fileInputElem.addEventListener("cancel", function(event) {
            document.body.removeChild(fileInputElem);
            
            // No file was selected, so nothing more to do.
            resultsHandler.@emu.jvic.gwt.GwtOpenFileResultsHandler::onFileResultsReady([Lemu/jvic/gwt/GwtOpenFileResult;)([]);
        });
        
        // Trigger the display of the open file dialog.
        fileInputElem.click();
    }-*/;
    
    @Override
    public void promptForTextInput(String message, String initialValue,
            TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                showHtmlPromptBox(message, initialValue, textInputResponseHandler);
            }
        });
    }

    private final native void showHtmlPromptBox(String message, String initialValue, TextInputResponseHandler textInputResponseHandler)/*-{
        var that = this;
        this.dialog.prompt(message, initialValue).then(function (res) {
            if (res) {
                textInputResponseHandler.@emu.jvic.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(true, res.prompt);
            } else {
                textInputResponseHandler.@emu.jvic.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(false, null);
            }
            that.@emu.jvic.gwt.GwtDialogHandler::dialogOpen = false;
        });
    }-*/;
    
    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                showHtmlAboutDialog(aboutMessage, textInputResponseHandler);
            }
        });
    }
    
    // TODO: Add url replacements to be suitable for VIC.
    private final native void showHtmlAboutDialog(String message, TextInputResponseHandler textInputResponseHandler)/*-{
        var that = this;
        message = message.replace(/(?:\r\n|\r|\n)/g, "<br>");
        message = message.replace(/https:\/\/github.com\/lanceewing\/jvic/g, "<a href='https://github.com/lanceewing/jvic' target='_blank'>https://github.com/lanceewing/jvic</a>");
        this.dialog.alert('', { 
                showStateButtons: false, 
                template:  '<b>' + message + '</b>'
            }).then(function (res) {
                if (res) {
                    if (res === true) {
                       // OK button.
                        textInputResponseHandler.@emu.jvic.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(true, "OK");
                    }
                    else {
                        textInputResponseHandler.@emu.jvic.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(true, res);
                    }
                }
                else {
                    textInputResponseHandler.@emu.jvic.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(false, null);
                }
                that.@emu.jvic.gwt.GwtDialogHandler::dialogOpen = false;
            });
    }-*/;
    
    @Override
    public boolean isDialogOpen() {
        return dialogOpen;
    }
    
}
