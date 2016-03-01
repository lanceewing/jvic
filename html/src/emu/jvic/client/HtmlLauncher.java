package emu.jvic.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;

import emu.jvic.JVicGdx;
import emu.jvic.ui.ConfirmHandler;
import emu.jvic.ui.ConfirmResponseHandler;

public class HtmlLauncher extends GwtApplication implements ConfirmHandler {

        @Override
        public GwtApplicationConfiguration getConfig () {
                return new GwtApplicationConfiguration(480, 320);
        }

        @Override
        public ApplicationListener createApplicationListener () {
                return new JVicGdx(this);
        }

        @Override
        public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
          // TODO: Show confirmation modal when this is invoked.
        }
}