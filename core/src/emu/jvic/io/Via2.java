package emu.jvic.io;

import emu.jvic.cpu.Cpu6502;
import emu.jvic.snap.Snapshot;

/**
 * Emulates the second of the VIC 20's two VIA chips. This one has all of port A 
 * and most of port B wired to the keyboard, with the main exception being the joystick 
 * right signal, which uses the top bit of port B.
 * 
 * @author Lance Ewing
 */
public class Via2 extends Via {

  /**
   * The CPU that the VIC 20 is using. This is where VIA 2 IRQ signals will be sent.
   */
  private Cpu6502 cpu6502;
  
  /**
   * The Keyboard from which we get the current keyboard state from.
   */
  private Keyboard keyboard;
  
  /**
   * The Joystick from which we get the current joystick state from.
   */
  private Joystick joystick;
  
  /**
   * Constructor for Via2.
   * 
   * @param cpu6502 The CPU that the VIC 20 is using. This is where VIA 2 IRQ signals will be sent.
   * @param keyboard The Keyboard from which we get the current keyboard state from.
   * @param joystick The Joystick from which the Via gets the current joystick state from.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Via2(Cpu6502 cpu6502, Keyboard keyboard, Joystick joystick, Snapshot snapshot) {
    super(true);
    this.cpu6502 = cpu6502;
    this.keyboard = keyboard;
    this.joystick = joystick;
    if (snapshot != null) {
      loadSnapshot(snapshot);
    }
  }
  
  /**
   * Initialises the VIA 2 chip with the state stored in the Snapshot.
   * 
   * @param snapshot The Snapshot to load the initial state of the VIA 2 chip from.
   */
  private void loadSnapshot(Snapshot snapshot) {
    // Load Port B state.
    outputRegisterB = snapshot.getVia2OutputRegisterB();
    inputRegisterB = snapshot.getVia2InputRegisterB();
    dataDirectionRegisterB = snapshot.getMemoryArray()[0x9122];
    updatePortBPins();
    
    // Load Port A state.
    outputRegisterA = snapshot.getVia2OutputRegisterA();
    inputRegisterA = snapshot.getVia2InputRegisterA();;
    dataDirectionRegisterA = snapshot.getMemoryArray()[0x9123];
    updatePortAPins();
    
    // Timer 1
    timer1CounterLow = snapshot.getMemoryArray()[0x9124];
    timer1CounterHigh = snapshot.getMemoryArray()[0x9125];
    timer1LatchLow = snapshot.getMemoryArray()[0x9126];
    timer1LatchHigh = snapshot.getMemoryArray()[0x9127];
    
    // Timer 2
    timer2LatchLow = snapshot.getVia2Timer2LatchLow();
    timer2CounterLow = snapshot.getVia2Timer2CounterLow();
    timer2CounterHigh = snapshot.getMemoryArray()[0x9129];
    
    // Shift Register
    shiftRegister = snapshot.getMemoryArray()[0x912A];
    
    // Load the Auxilliary Control Register state.
    auxiliaryControlRegister = snapshot.getMemoryArray()[0x912B];
    timer1PB7Mode = (auxiliaryControlRegister & 0x80) >> 7;
    timer1Mode = (auxiliaryControlRegister & 0x40) >> 6;
    timer2Mode = (auxiliaryControlRegister & 0x20) >> 5;
    shiftRegisterMode = (auxiliaryControlRegister & 0x1C) >> 2;
    portALatchMode = (auxiliaryControlRegister & 0x01);
    portBLatchMode = (auxiliaryControlRegister & 0x02) >> 1;
    
    peripheralControlRegister = snapshot.getMemoryArray()[0x912C];
    
    interruptFlagRegister = snapshot.getVia2InterruptFlagRegister();
    interruptEnableRegister = snapshot.getVia2InterruptEnableRegister();
  }
  
  /**
   * Returns the current values of the Port A pins.
   *
   * @return the current values of the Port A pins.
   */
  protected int getPortAPins() {
    return keyboard.scanKeyboardRow(~super.getPortBPins());
  }
  
  /**
   * Returns the current values of the Port B pins.
   *
   * @return the current values of the Port B pins.
   */
  protected int getPortBPins() {
    // The bottom 7 bits are for the keyboard column scan.
    int value = (keyboard.scanKeyboardColumn(~super.getPortAPins()) & 0x7F);
    
    // The top bit is for the joystick right signal.
    value |= (joystick.getJoystickState() & 0x80);
    
    return value;
  }
  
  /**
   * Notifies the 6502 of the change in the state of the IRQ pin. In this case 
   * it is the 6502's NMI pin that this 6522 IRQ is connected to.
   * 
   * @param The current state of this VIA chip's IRQ pin (1 or 0).
   */
  protected void updateIrqPin(int pinState) {
    if (pinState == 1) {
      cpu6502.setInterrupt(Cpu6502.S_IRQ);
    } else {
      cpu6502.clearInterrupt(Cpu6502.S_IRQ);
    }
  }
}
