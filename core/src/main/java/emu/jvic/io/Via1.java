package emu.jvic.io;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.snap.Snapshot;

/**
 * Emulates the first of the VIC 20's two VIA chips. This one has most of the joystick
 * wired up to port A (fire, left, down and up). It also handles the RESTORE key. 
 *  
 * @author Lance Ewing
 */
public class Via1 extends Via6522 {

  /**
   * The CPU that the VIC 20 is using. This is where VIA 1 IRQ signals will be sent.
   */
  private Cpu6502 cpu6502;
  
  /**
   * The Joystick from which we get the current joystick state from.
   */
  private Joystick joystick;
  
  /**
   * The SerialBus that VIA 1 is connected to for Data IN, Clock IN and Atn OUT.
   */
  private SerialBus serialBus;
  
  /**
   * Constructor for Via1.
   * 
   * @param cpu6502 The CPU that the VIC 20 is using. This is where VIA 2 IRQ signals will be sent.
   * @param joystick The Joystick from which the Via gets the current joystick state from.
   * @param serialBus The SerialBus that VIA 1 is connected to for Data IN, Clock IN and Atn OUT.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Via1(Cpu6502 cpu6502, Joystick joystick, SerialBus serialBus, Snapshot snapshot) {
    super(false);
    this.cpu6502 = cpu6502;
    this.joystick = joystick;
    this.serialBus = serialBus;
    if (snapshot != null) {
      loadSnapshot(snapshot);
    }
  }
  
  /**
   * Initialises the VIA 1 chip with the state stored in the Snapshot.
   * 
   * @param snapshot The Snapshot to load the initial state of the VIA 1 chip from.
   */
  private void loadSnapshot(Snapshot snapshot) {
    // Load Port B state.
    outputRegisterB = snapshot.getVia1OutputRegisterB();
    inputRegisterB = snapshot.getVia1InputRegisterB();
    dataDirectionRegisterB = snapshot.getMemoryArray()[0x9112];
    updatePortBPins();
    
    // Load Port A state.
    outputRegisterA = snapshot.getVia1OutputRegisterA();
    inputRegisterA = snapshot.getVia1InputRegisterA();;
    dataDirectionRegisterA = snapshot.getMemoryArray()[0x9113];
    updatePortAPins();
    
    // Timer 1
    int timer1CounterLow = snapshot.getMemoryArray()[0x9114];
    int timer1CounterHigh = snapshot.getMemoryArray()[0x9115];
    int timer1LatchLow = snapshot.getMemoryArray()[0x9116];
    int timer1LatchHigh = snapshot.getMemoryArray()[0x9117];
    timer1Counter = ((timer1CounterHigh << 8) + timer1CounterLow);
    timer1Latch = ((timer1LatchHigh << 8) + timer1LatchLow);
    
    // Timer 2
    timer2Latch = snapshot.getVia1Timer2LatchLow();
    int timer2CounterLow = snapshot.getVia1Timer2CounterLow();
    int timer2CounterHigh = snapshot.getMemoryArray()[0x9119];
    timer2Counter = ((timer2CounterHigh << 8) + timer2CounterLow);
    
    // Shift Register
    shiftRegister = snapshot.getMemoryArray()[0x911A];
    
    // Load the Auxilliary Control Register state.
    auxiliaryControlRegister = snapshot.getMemoryArray()[0x911B];
    timer1PB7Mode = (auxiliaryControlRegister & 0x80) >> 7;
    timer1Mode = (auxiliaryControlRegister & 0x40) >> 6;
    timer2Mode = (auxiliaryControlRegister & 0x20) >> 5;
    shiftRegisterMode = (auxiliaryControlRegister & 0x1C) >> 2;
    portALatchMode = (auxiliaryControlRegister & 0x01);
    portBLatchMode = (auxiliaryControlRegister & 0x02) >> 1;
    
    peripheralControlRegister = snapshot.getMemoryArray()[0x911C];
    
    interruptFlagRegister = snapshot.getVia1InterruptFlagRegister();
    interruptEnableRegister = snapshot.getVia1InterruptEnableRegister();
  }
  
  /**
   * Returns the current values of the Port A pins.
   *
   * @return the current values of the Port A pins.
   */
  public int getPortAPins() {
    // PA0: Serial Clock IN
    // PA1: Serial Data IN
    // PA2: Joystick UP
    // PA3: Joystick DOWN
    // PA4: Joystick LEFT
    // PA5: Joystick FIRE
    // PA6: Tape Sense
    // PA7: Serial Atn OUT
    
    // NOTE: The 1541 and VIC 20 differ in how the serial lines map to their VIA port bits. In the
    // 1541, there is an inverter between the IN and the VIA, bit in the VIC 20, there isn't.
    int value = ((super.getPortAPins() & 0xC0) |
        (joystick.getJoystickState() & 0x3C) |
        (serialBus.getData()? 0x00 : 0x02) | 
        (serialBus.getClock()? 0x00 : 0x01));
    
    return value;
  }
  
  /**
   * Invoked whenever the ORA or DDRA register of the VIA is written to.
   */
  protected void updatePortAPins() {
    // Refresh the state of Port A pins based on ORA, DDRA, and current port A pin state.
    super.updatePortAPins();
    
    // Now used refreshed portAPins to update serial bus. PA7 is ATN OUT.
    if ((portAPins & 0x80) == 0x80) {
      serialBus.pullDownAtn(this);
    } else {
      serialBus.releaseAtn(this);
    }
  }
  
  /**
   * Notifies the 6502 of the change in the state of the IRQ pin. In this case 
   * it is the 6502's NMI pin that this 6522 IRQ is connected to.
   * 
   * @param The current state of this VIA chip's IRQ pin (1 or 0).
   */
  protected void updateIrqPin(int pinState) {
    if (pinState == 1) {
      cpu6502.setInterrupt(Cpu6502.S_NMI);
    } else {
      cpu6502.clearInterrupt(Cpu6502.S_NMI);
    }
  } 
}
