package emu.jvic;

import java.util.function.Consumer;

import emu.jvic.config.AppConfigItem;

public interface ProgramLoader {

    void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer);
    
}
