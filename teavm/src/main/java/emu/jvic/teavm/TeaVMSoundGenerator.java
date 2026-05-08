package emu.jvic.teavm;

import emu.jvic.MachineType;
import emu.jvic.sound.SoundGenerator;

public class TeaVMSoundGenerator extends SoundGenerator {

    private boolean soundOn;

    @Override
    public void initSound(MachineType machineType) {
        soundOn = false;
    }

    @Override
    public void emulateCycle() {
        // Audio is intentionally stubbed in the Phase 1 TeaVM bootstrap.
    }

    @Override
    public void pauseSound() {
        soundOn = false;
    }

    @Override
    public void resumeSound() {
        soundOn = true;
    }

    @Override
    public boolean isSoundOn() {
        return soundOn;
    }

    @Override
    public void dispose() {
        soundOn = false;
    }
}