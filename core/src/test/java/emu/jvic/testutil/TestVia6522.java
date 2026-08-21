package emu.jvic.testutil;

import emu.jvic.io.Via6522;

/**
 * Minimal concrete subclass of the (otherwise usable-as-is) {@link Via6522},
 * exposing hooks for the template methods that real VIC-20 wiring (Via1/Via2)
 * normally overrides: the IRQ pin, and the CA1/CB1/CA2/CB2 input pins. Tests
 * drive {@link #ca1Input}/{@link #cb1Input}/{@link #ca2Input}/{@link #cb2Input}
 * directly and call {@code emulateCycle()} to have the base class detect
 * transitions, exactly as real external hardware would.
 */
public class TestVia6522 extends Via6522 {

    /** Current value the base class will read via {@link #getCa1()}. */
    public int ca1Input = 0;

    /** Current value the base class will read via {@link #getCb1()}. */
    public int cb1Input = 0;

    /** Current value the base class will read via {@link #getCa2Input()}. */
    public int ca2Input = 0;

    /** Current value the base class will read via {@link #getCb2Input()}. */
    public int cb2Input = 0;

    /** Latest pin state reported via {@link #updateIrqPin(int)}. */
    public int irqPinState = 0;

    /** Number of times {@link #updateIrqPin(int)} was called with a CHANGED value. */
    public int irqPinChangeCount = 0;

    /** Number of times {@link #updateControlLines()} was invoked. */
    public int controlLinesUpdateCount = 0;

    public TestVia6522(boolean autoResetIrq) {
        super(autoResetIrq);
    }

    @Override
    protected int getCa1() {
        return ca1Input;
    }

    @Override
    protected int getCb1() {
        return cb1Input;
    }

    @Override
    protected int getCa2Input() {
        return ca2Input;
    }

    @Override
    protected int getCb2Input() {
        return cb2Input;
    }

    @Override
    protected void updateIrqPin(int pinState) {
        if (pinState != irqPinState) {
            irqPinChangeCount++;
        }
        irqPinState = pinState;
    }

    @Override
    protected void updateControlLines() {
        controlLinesUpdateCount++;
    }

    /**
     * Directly drives the internal CB2 pin value, for testing shift-register
     * "shift in" modes, where real hardware uses CB2 as a serial data input. The
     * shift-register logic reads the raw {@code cb2} field directly (not via
     * {@link #getCb2Input()}, which is only consulted for CB2 *interrupt-mode*
     * transition detection), so this reaches the protected field directly.
     */
    public void setCb2(int value) {
        this.cb2 = value;
    }
}
