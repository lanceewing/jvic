package emu.jvic.ui;

/**
 * Interface that is called by a ConfirmHandler implementation when the user has
 * chosen either Yes or No.
 * 
 * @author Lance Ewing
 */
public interface ConfirmResponseHandler {

    public void yes();

    public void no();

}
