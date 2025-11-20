package emu.jvic.ui;

/**
 * An interface that the different platforms can implement to provide different
 * types of dialog window, e.g. confirm dialog, file chooser, etc.
 * 
 * @author Lance Ewing
 */
public interface DialogHandler {

    /**
     * Invoked when JOric wants to confirm with the user that they really want to
     * continue with a particular action.
     * 
     * @param message                The message to be displayed to the user.
     * @param confirmResponseHandler The handler to be invoked with the user's response.
     */
    public void confirm(String message, ConfirmResponseHandler confirmResponseHandler);

    /**
     * Invoked when JOric wants the user to choose a file to open.
     * 
     * @param title                   Title for the open file dialog.
     * @param startPath               The starting path.
     * @param openFileResponseHandler The handler to be invoked with the chosen file (if chosen).
     */
    public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler);

    /**
     * Invoked when AGILE wants to ask what type of game import to perform.
     * 
     * @param appConfigItem             Optional selected game that is being imported.
     * @param importTypeResponseHandler The handler to be invoked with the user's response.
     */
    public void promptForTextInput(String message, String initialValue,
            TextInputResponseHandler textInputResponseHandler);

    /**
     * Shows the About AGILE message dialog.
     * 
     * @param aboutMessage             The About message to display.
     * @param textInputResponseHandler Optional state management button response.
     */
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler);

    /**
     * Returns true if a dialog is currently open.
     * 
     * @return true if a dialog is currently open.
     */
    public boolean isDialogOpen();

}
