package emu.jvic.io;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.snap.Snapshot;

/**
 * Emulates the first of the VIC 20's two VIA chips. This one has most of the joystick
 * wired up to port A (fire, left, down and up). It also handles the RESTORE key. 
 *  
 * @author Lance Ewing
 */
public class Via1 extends Via {

  /**
   * The CPU that the VIC 20 is using. This is where VIA 1 IRQ signals will be sent.
   */
  private Cpu6502 cpu6502;
  
  /**
   * The Joystick from which we get the current joystick state from.
   */
  private Joystick joystick;
  
  /**
   * Constructor for Via1.
   * 
   * @param cpu6502 The CPU that the VIC 20 is using. This is where VIA 2 IRQ signals will be sent.
   * @param joystick The Joystick from which the Via gets the current joystick state from.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Via1(Cpu6502 cpu6502, Joystick joystick, Snapshot snapshot) {
    super(false);
    this.cpu6502 = cpu6502;
    this.joystick = joystick;
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
    timer1CounterLow = snapshot.getMemoryArray()[0x9114];
    timer1CounterHigh = snapshot.getMemoryArray()[0x9115];
    timer1LatchLow = snapshot.getMemoryArray()[0x9116];
    timer1LatchHigh = snapshot.getMemoryArray()[0x9117];
    
    // Timer 2
    timer2LatchLow = snapshot.getVia1Timer2LatchLow();
    timer2CounterLow = snapshot.getVia1Timer2CounterLow();
    timer2CounterHigh = snapshot.getMemoryArray()[0x9119];
    
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
  protected int getPortAPins() {
    return (joystick.getJoystickState() | (super.getPortAPins() & 0xC3));
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
