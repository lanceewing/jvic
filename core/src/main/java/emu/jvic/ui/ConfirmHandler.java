package emu.jvic.ui;

/**
 * An interface that the different platforms can implement to provide a way for
 * the user to confirm an action, such as a pop up confirm dialog.
 * 
 * @author Lance Ewing
 */
public interface ConfirmHandler {

    /**
     * Invoked when JVic wants to confirm with the user that they really want to
     * continue with a particular action.
     * 
     * @param message                The message to be displayed to the user.
     * @param confirmResponseHandler The handler to be invoked with the user's response.
     */
    public void confirm(String message, ConfirmResponseHandler confirmResponseHandler);

}
