package emu.jvic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * Handles the input of keyboard events, mapping them to a form that the JVic
 * emulator can query as required. The storage of the state is platform dependent,
 * which is why this class is abstract. This is primarily due to the HTML5/GWT/web 
 * implementation needing to store that state in a form that can be immediately 
 * accessed by both the UI thread (for updates) and the web worker thread (for
 * querying).
 */
public abstract class KeyboardMatrix extends InputAdapter {

    /**
     * HashMap used to store mappings between Java key events and Oric
     * keyboard scan codes.
     */
    private HashMap<Integer, int[]> keyConvHashMap;
    
    /**
     * Holds the last time that the key was pressed down, or 0 if it has since been released.
     */
    private long minKeyReleaseTimes[] = new long[512];
    
    /**
     * Holds a queue of keycodes whose key release processing has been delayed. This is
     * supported primarily for use with the Android virtual keyboard on some devices, 
     * where the key pressed and release both get fired on release of the key, so have
     * virtually no time between them.
     */
    private TreeMap<Long, Integer> delayedReleaseKeys = new TreeMap<Long, Integer>();
    
    private int lastKeyDownKeycode;
    
    /**
     * Checks if there are any keys whose release processed has been delayed that
     * are now able to be processed due to the minimum release time having been
     * passed.
     */
    public void checkDelayedReleaseKeys() {
        if (!delayedReleaseKeys.isEmpty()) {
            synchronized (delayedReleaseKeys) {
                List<Long> processedReleases = new ArrayList<Long>();
                processedReleases.addAll(delayedReleaseKeys.headMap(TimeUtils.nanoTime()).keySet());
                for (Long keyReleaseTime : processedReleases) {
                    int delayedReleaseKeyCode = delayedReleaseKeys.remove(keyReleaseTime);
                    keyUp(delayedReleaseKeyCode);
                }
            }
        }
    }
    
    public abstract int getKeyMatrixRow(int row);
    
    public abstract void setKeyMatrixRow(int row, int value);
    
}
