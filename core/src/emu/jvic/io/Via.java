package emu.jvic.io;

import emu.jvic.memory.MemoryMappedChip;

/**
 * This class emulates a 6522 VIA IO/timer chip. It does not emulate input latching 
 * or any control line features since the VIC 20's usage of these are restricted to 
 * such features as the Serial Bus, RS232 and Tape R/W. These are features not 
 * normally emulated since it would usually require emulation at a level that is 
 * finer than cycle based. And besides, in the case of tapes and disks, there are 
 * much faster ways for an emulator to load the images than to rely on the VIA.
 * 
 * @author Lance Ewing
 */
public class Via extends MemoryMappedChip {

  // Constants for the 16 internal memory mapped registers.
  private static final int VIA_REG_0 = 0;
  private static final int VIA_REG_1 = 1;
  private static final int VIA_REG_2 = 2;
  private static final int VIA_REG_3 = 3;
  private static final int VIA_REG_4 = 4;
  private static final int VIA_REG_5 = 5;
  private static final int VIA_REG_6 = 6;
  private static final int VIA_REG_7 = 7;
  private static final int VIA_REG_8 = 8;
  private static final int VIA_REG_9 = 9;
  private static final int VIA_REG_10 = 10;
  private static final int VIA_REG_11 = 11;
  private static final int VIA_REG_12 = 12;
  private static final int VIA_REG_13 = 13;
  private static final int VIA_REG_14 = 14;
  private static final int VIA_REG_15 = 15;
  
  // Constants for the INTERRUPT bits of the IFR.
  private static final int IRQ_RESET        = 0x7F;
  private static final int TIMER1_RESET     = 0xBF;
  private static final int TIMER2_RESET     = 0xDF;
  private static final int CB1_RESET        = 0xEF;
  private static final int CB2_RESET        = 0xF7;
  private static final int CB1_AND_2_RESET  = (CB1_RESET & CB2_RESET);
  private static final int SHIFT_RESET      = 0xFB;
  private static final int CA1_RESET        = 0xFD;
  private static final int CA2_RESET        = 0xFE;
  private static final int CA1_AND_2_RESET  = (CA1_RESET & CA2_RESET);
  private static final int IRQ_SET          = 0x80;
  private static final int TIMER1_SET       = 0x40;
  private static final int TIMER2_SET       = 0x20;
  private static final int CB1_SET          = 0x10;
  private static final int CB2_SET          = 0x08;
  private static final int SHIFT_SET        = 0x04;
  private static final int CA1_SET          = 0x02;
  private static final int CA2_SET          = 0x01;
  
  // Constants for timer modes.
  private static final int ONE_SHOT         = 0x00;
  
  // Port B
  protected int outputRegisterB;            // Reg 0 WRITE?   (Reg 15 but no handshake)
  protected int inputRegisterB;             // Reg 0 READ?    (Reg 15 but no handshake)
  protected int portBPins;
  protected int dataDirectionRegisterB;     // Reg 2
  
  // Port A
  protected int outputRegisterA;            // Reg 1
  protected int inputRegisterA;
  protected int portAPins;
  protected int dataDirectionRegisterA;     // Reg 3
  
  // Timer 1
  protected int timer1CounterLow;           // Reg 4 READ
  protected int timer1CounterHigh;          // Reg 5
  protected int timer1LatchLow;             // Reg 6 READ-WRITE / Reg 4 WRITE
  protected int timer1LatchHigh;            // Reg 7
  
  // Timer 2
  protected int timer2LatchLow;             // Reg 8 WRITE
  protected int timer2CounterLow;           // Reg 8 READ
  protected int timer2CounterHigh;          // Reg 9
  
  // Shift Register
  protected int shiftRegister;              // Reg 10
  
  protected int auxiliaryControlRegister;   // Reg 11
  protected int peripheralControlRegister;  // Reg 12
  protected int interruptFlagRegister;      // Reg 13
  protected int interruptEnableRegister;    // Reg 14
  
  protected int timer1PB7Mode;
  protected int timer1Mode;
  protected int timer2Mode;
  protected int shiftRegisterMode;
  protected int portALatchMode;
  protected int portBLatchMode;
  
  /**
   * This flag is set to true when timer1 is operating in the one shot mode and
   * has just reached zero.
   */
  private boolean timer1HasShot;
  
  /**
   * True when timer2 has just reached zero.
   */
  private boolean timer2HasShot;
  
  /**
   * Whether to reset the IRQ signal when the IRQ flags reset.
   */
  private boolean autoResetIrq;
  
  /**
   * Constructor for VIA6522.
   * 
   * @param autoResetIrq whether to reset the IRQ signal when the IRQ flags reset
   */
  public Via(boolean autoResetIrq) {
    this.autoResetIrq = autoResetIrq;
  }
  
  /**
   * Writes a byte into one of the 16 VIA registers.
   * 
   * @param address The address to write to.
   * @param value The byte to write into the address.
   */
  public void writeMemory(int address, int value) {
    switch (address & 0x000F) {
      case VIA_REG_0: // ORB/IRB
        outputRegisterB = value;
        updatePortBPins();
        interruptFlagRegister &= CB1_AND_2_RESET;
        updateIFRTopBit();
        break;

      case VIA_REG_1: // ORA/IRA
        outputRegisterA = value;
        updatePortAPins();
        interruptFlagRegister &= CA1_AND_2_RESET;
        updateIFRTopBit();
        break;

      case VIA_REG_2: // DDRB
        dataDirectionRegisterB = value;
        updatePortBPins();
        break;

      case VIA_REG_3: // DDRA
        dataDirectionRegisterA = value;
        updatePortAPins();
        break;
  
      case VIA_REG_4: // Timer 1 low-order counter
        timer1LatchLow = value;
        break;
  
      case VIA_REG_5: // Timer 1 high-order counter
        timer1LatchHigh = value;
        timer1CounterHigh = value;
        timer1CounterLow = timer1LatchLow;
        interruptFlagRegister &= TIMER1_RESET;
        updateIFRTopBit();
        timer1HasShot = false;
        break;
  
      case VIA_REG_6: // Timer 1 low-order latches
        timer1LatchLow = value;
        break;
  
      case VIA_REG_7: // Timer 1 high-order latches
        timer1LatchHigh = value;
        break;
  
      case VIA_REG_8: // Timer 2 low-order counter
        timer2LatchLow = value;
        break;
  
      case VIA_REG_9: // Timer 2 high-order counter
        timer2CounterHigh = value;
        timer2CounterLow = timer2LatchLow;
        interruptFlagRegister &= TIMER2_RESET;
        updateIFRTopBit();
        timer2HasShot = false;
        break;
  
      case VIA_REG_10: // Shift Register.
        shiftRegister = value;
        interruptFlagRegister &= SHIFT_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_11: // Auxiliary Control Register.
        auxiliaryControlRegister = value;
        timer1PB7Mode = (value & 0x80) >> 7;
        timer1Mode = (value & 0x40) >> 6;
        timer2Mode = (value & 0x20) >> 5;
        shiftRegisterMode = (value & 0x1C) >> 2;
        portALatchMode = (value & 0x01);
        portBLatchMode = (value & 0x02) >> 1;
        break;
  
      case VIA_REG_12: // Peripheral Control Register.
        peripheralControlRegister = value;
        break;
  
      case VIA_REG_13: // Interrupt Flag Register
        // Note: Top bit cannot be cleared directly
        interruptFlagRegister &= ~(value & 0x7f);
        updateIFRTopBit();
        break;
  
      case VIA_REG_14: // Interrupt Enable Register
        if ((value & 0x80) == 0) {
          interruptEnableRegister &= ~(value & 0xff);
        } else {
          interruptEnableRegister |= (value & 0xff);
        }
        interruptEnableRegister &= 0x7f;
        updateIFRTopBit();
        break;
  
      case VIA_REG_15: // ORA/IRA (no handshake)
        outputRegisterA = value;
        updatePortAPins();
        break;
      }
  }

  private static final int PORTB_INPUT_LATCHING = 0x02;
  private static final int PORTA_INPUT_LATCHING = 0x01;

  /**
   * Reads a value from one of the 16 VIA registers.
   * 
   * @param address The address to read the register value from.
   */
  public int readMemory(int address) {
    int value = 0;

    switch (address & 0x000F) {
      case VIA_REG_0: // ORB/IRB
        if ((auxiliaryControlRegister & PORTB_INPUT_LATCHING) == 0) {
          // If you read a pin on IRB and the pin is set to be an input (with
          // latching
          // disabled), then you will read the current state of the corresponding
          // PB pin.
          value = getPortBPins() & (~dataDirectionRegisterB);
        } else {
          // If you read a pin on IRB and the pin is set to be an input (with
          // latching
          // enabled), then you will read the actual IRB.
          value = inputRegisterB & (~dataDirectionRegisterB);
        }
        // If you read a pin on IRB and the pin is set to be an output, then you
        // will
        // actually read ORB, which contains the last value that was written to
        // port B.
        value = value | (outputRegisterB & dataDirectionRegisterB);
        interruptFlagRegister &= CB1_AND_2_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_1: // ORA/IRA
        if ((auxiliaryControlRegister & PORTA_INPUT_LATCHING) == 0) {
          // If you read a pin on IRA and input latching is disabled for port A,
          // then you will simply read the current state of the corresponding PA
          // pin, REGARDLESS of whether that pin is set to be an input or an
          // output.
          value = getPortAPins();
        } else {
          // If you read a pin on IRA and input latching is enabled for port A,
          // then you will read the actual IRA, which is the last value that was
          // latched into IRA.
          value = inputRegisterA;
        }
        interruptFlagRegister &= CA1_AND_2_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_2: // DDRB
        value = dataDirectionRegisterB;
        break;
  
      case VIA_REG_3: // DDRA
        value = dataDirectionRegisterA;
        break;
  
      case VIA_REG_4: // Timer 1 low-order counter
        value = timer1CounterLow;
        interruptFlagRegister &= TIMER1_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_5: // Time 1 high-order counter
        value = timer1CounterHigh;
        break;
  
      case VIA_REG_6: // Timer 1 low-order latches
        value = timer1LatchLow;
        break;
  
      case VIA_REG_7: // Timer 1 high-order latches
        value = timer1LatchHigh;
        break;
  
      case VIA_REG_8: // Timer 2 low-order counter
        value = timer2CounterLow;
        interruptFlagRegister &= TIMER2_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_9: // Timer 2 high-order counter
        value = timer2CounterHigh;
        break;
  
      case VIA_REG_10: // Shift register
        value = shiftRegister;
        interruptFlagRegister &= SHIFT_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_11: // Auxiliary control register
        value = auxiliaryControlRegister;
        break;
  
      case VIA_REG_12: // Peripheral Control Register
        value = peripheralControlRegister;
        break;
  
      case VIA_REG_13: // Interrupt Flag Register
        updateIFRTopBit();
        value = interruptFlagRegister;
        break;
  
      case VIA_REG_14: // Interrupt Enable Register
        value = (interruptEnableRegister & 0x7F);
        break;
  
      case VIA_REG_15: // ORA/IRA (no handshake)
        if ((auxiliaryControlRegister & PORTA_INPUT_LATCHING) == 0) {
          // If you read a pin on IRA and input latching is disabled for port A,
          // then you will simply read the current state of the corresponding PA
          // pin, REGARDLESS of whether that pin is set to be an input or an
          // output.
          value = getPortAPins();
        } else {
          // If you read a pin on IRA and input latching is enabled for port A,
          // then you will read the actual IRA, which is the last value that was
          // latched into IRA.
          value = inputRegisterA;
        }
        break;
    }

    return value;
  }

  /**
   * Returns a string containing details about the current state of the chip.
   * 
   * @return a string containing details about the current state of the chip.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer();

    buf.append("peripheralControlRegister: ");
    buf.append(Integer.toBinaryString(peripheralControlRegister));
    buf.append("\n");
    buf.append("auxiliaryControlRegister: ");
    buf.append(Integer.toBinaryString(auxiliaryControlRegister));
    buf.append("\n");
    buf.append("timer1 latch: ");
    buf.append(Integer.toHexString((timer1LatchHigh << 8) + timer1LatchLow));
    buf.append("\n");
    buf.append("timer1 counter: ");
    buf.append(Integer.toHexString((timer1CounterHigh << 8) + timer1CounterLow));
    buf.append("\n");
    buf.append("timer2 counter: ");
    buf.append(Integer.toHexString((timer2CounterHigh << 8) + timer2CounterLow));
    buf.append("\n");
    buf.append("interruptEnableRegister: ");
    buf.append(Integer.toHexString(interruptEnableRegister));
    buf.append("\n");
    buf.append("interruptFlagRegister: ");
    buf.append(Integer.toHexString(interruptFlagRegister));
    buf.append("\n");

    return (buf.toString());
  }

  /**
   * Updates the IFR top bit and sets the IRQ pin if needed.
   */
  protected void updateIFRTopBit() {
    // The top bit says whether any interrupt is active and enabled
    if ((interruptFlagRegister & (interruptEnableRegister & 0x7f)) == 0) {
      interruptFlagRegister &= IRQ_RESET;
      if (autoResetIrq) {
        // TODO: Only do this if IRQ was set last time we checked (i.e. the
        // change is what we react to)???
        updateIrqPin(0);
      }
    } else {
      interruptFlagRegister |= IRQ_SET;
      // TODO: Only do this if IRQ was not set last time we checked (i.e. the
      // change is what we react to????
      updateIrqPin(1);
    }
  }

  /**
   * Template method for subclasses to decide what the IRQ pin is connected to.
   */
  protected void updateIrqPin(int pinState) {
  }

  /**
   * Emulates a single cycle of this VIA chip.
   */
  public void emulateCycle() {
    // Decrement the 2 timers.
    if (timer1Mode == ONE_SHOT) {
      // Timed interrupt each time T1 is loaded (one shot)
      timer1CounterLow--;
      if (timer1CounterLow < 0) {
        timer1CounterHigh--;
        if (timer1CounterHigh < 0) {
          // Counter continues to count down from 0xFFFF
          timer1CounterHigh = 0xFF;
          timer1CounterLow = 0xFF;

          // Set the interrupt flag only if the timer has been reloaded.
          if (!timer1HasShot) {
            interruptFlagRegister |= TIMER1_SET;
            updateIFRTopBit();
            timer1HasShot = true;
          }
        } else {
          timer1CounterLow = 0xFF;
        }
      }
    } else {
      // Continuous interrupts (free-running)
      timer1CounterLow--;
      if (timer1CounterLow < 0) {
        timer1CounterHigh--;
        if (timer1CounterHigh < 0) {
          // Reload from latches and generate interrupt.
          timer1CounterHigh = timer1LatchHigh;
          timer1CounterLow = timer1LatchLow;
          interruptFlagRegister |= TIMER1_SET;
          updateIFRTopBit();
          timer1HasShot = true;
        } else {
          timer1CounterLow = 0xFF;
        }
      }
    }

    if (timer2Mode == ONE_SHOT) {
      // Timed interrupt (one shot)
      timer2CounterLow--;
      if (timer2CounterLow < 0) {
        timer2CounterHigh--;
        if (timer2CounterHigh < 0) {
          // Countinues to count down from 0xFFFF
          timer2CounterHigh = 0xFF;
          timer2CounterLow = 0xFF;

          // Set the interrupt flag only if the timer has been reloaded.
          if (!timer2HasShot) {
            interruptFlagRegister |= TIMER2_SET;
            updateIFRTopBit();
            timer2HasShot = true;
          }
        } else {
          timer2CounterLow = 0xFF;
        }
      }
    }

    // Shift the shift register.
    if (shiftRegisterMode != 0) {
      // TODO: Implement shift register.
      switch (shiftRegisterMode) {
      case 0x00:
        break;
      case 0x01:
        break;
      case 0x02:
        break;
      case 0x03:
        break;
      case 0x04:
        break;
      case 0x05:
        break;
      case 0x06:
        break;
      case 0x07:
        break;
      }
    }
  }
  
  /**
   * Updates the state of the Port A pins based on the current values of the 
   * ORA and DDRA.
   */
  protected void updatePortAPins() {
    // Any pins that are inputs must be left untouched. 
    int inputPins = (portAPins & (~dataDirectionRegisterA));
    
    // Pins that are outputs should be set to 1 or 0 depending on what is in the ORA.
    int outputPins = (outputRegisterA & dataDirectionRegisterA);
    
    portAPins = inputPins | outputPins; 
  }
  
  /**
   * Updates the state of the Port B pins based on the current values of the 
   * ORB and DDRB.
   */
  protected void updatePortBPins() {
    // Any pins that are inputs must be left untouched. 
    int inputPins = (portBPins & (~dataDirectionRegisterB));
    
    // Pins that are outputs should be set to 1 or 0 depending on what is in the ORB.
    int outputPins = (outputRegisterB & dataDirectionRegisterB);
    
    portBPins = inputPins | outputPins;
  }
  
  /**
   * Returns the current values of the Port A pins.
   *
   * @return the current values of the Port A pins.
   */
  protected int getPortAPins() {
    return portAPins;
  }
  
  /**
   * Returns the current values of the Port B pins.
   *
   * @return the current values of the Port B pins.
   */
  protected int getPortBPins() {
    return portBPins;
  }
}
