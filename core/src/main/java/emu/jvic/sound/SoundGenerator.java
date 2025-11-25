package emu.jvic.sound;

import emu.jvic.MachineType;

/**
 * Interface defining the operations required of an VIC sound generator implementation. 
 * This has been defined mainly so that a j2se, libgdx, and android implementation can be 
 * built but the jvic-core depends only on this interface.
 * 
 * @author Lance Ewing
 */
public abstract class SoundGenerator {

    protected int[] mem;
    
    public void init(int[] mem, MachineType machineType) {
        this.mem = mem;
        this.initSound(machineType);
    }
    
    public abstract void initSound(MachineType machineType);
    
    public abstract void emulateCycle();
    
    public abstract void pauseSound();
    
    public abstract void resumeSound();
    
    public abstract boolean isSoundOn();

    public abstract void dispose();
    
}
