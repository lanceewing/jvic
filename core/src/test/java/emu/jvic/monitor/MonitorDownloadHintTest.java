package emu.jvic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import emu.jvic.MachineType;
import emu.jvic.memory.RamType;
import emu.jvic.testutil.MachineTestHarness;

/**
 * Tests the browser's one-time hint that written files are in the browser's store
 * and can be saved with download.
 */
public class MonitorDownloadHintTest {

    private static MonitorEngine engine(boolean browser) {
        MachineTestHarness h = MachineTestHarness.withSyntheticRoms(MachineType.PAL, RamType.RAM_UNEXPANDED);
        MonitorEngine engine = new MonitorEngine(h.machine, new MonitorHost() {
            @Override
            public void resume() {
            }

            @Override
            public void quit() {
            }

            @Override
            public boolean isWarp() {
                return false;
            }

            @Override
            public void setWarp(boolean warp) {
            }

            @Override
            public void print(String text) {
            }

            @Override
            public boolean isFileStoreHidden() {
                return browser;
            }
        });
        engine.setFileSystem(new InMemoryMonitorFileSystem());
        return engine;
    }

    private static String run(MonitorEngine engine, String line) {
        StringBuilder out = new StringBuilder();
        engine.execute(line, out::append);
        return out.toString();
    }

    @Test
    void theBrowserSaysOnceHowToDownloadAWrittenFile() {
        MonitorEngine engine = engine(true);
        assertEquals("Saving file 'a.prg' from $1000 to $1001\n"
                + "Saved in the browser's file store. Use download \"a.prg\" to save it to your computer.\n",
                run(engine, "save \"a.prg\" 0 1000 1001"));
        assertEquals("Saving file 'b.prg' from $1000 to $1001\n", run(engine, "save \"b.prg\" 0 1000 1001"));
        assertEquals("", run(engine, "dump \"s.jvs\""));
    }

    @Test
    void otherWritesAlsoGiveTheHint() {
        MonitorEngine log = engine(true);
        assertEquals("Saved in the browser's file store. Use download \"monitor.log\" to save it to your computer.\n",
                run(log, "log on"));
        MonitorEngine shot = engine(true);
        assertEquals("Saved in the browser's file store. Use download \"s.bmp\" to save it to your computer.\n",
                run(shot, "scrsh \"s\""));
    }

    @Test
    void desktopNeverShowsIt() {
        MonitorEngine engine = engine(false);
        assertEquals("Saving file 'a.prg' from $1000 to $1001\n", run(engine, "save \"a.prg\" 0 1000 1001"));
        assertEquals("", run(engine, "dump \"s.jvs\""));
    }
}
