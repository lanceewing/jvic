package emu.jvic.io.tape;

/**
 * Emulates the Commodore 1530 datasette (tape drive). Currently, this is only a very
 * simple emulation, providing only what is needed to read a .TAP file, for the purposes
 * of loading a program to run. There is no support for writing.
 */
public class C1530Datasette {
    
    /**
     * Holds all of the data of the currently loaded .TAP tape image.
     */
    private TapeImage tape;

    /**
     * Whether the datasette motor is on or not.
     */
    private boolean motorOn;
    
    /**
     * If non-zero, and the motor is on, then holds a time after which the motor will be 
     * turned off. Has no effect if zero.
     */
    private int motorTimeToLive;
    
    /**
     * The "tape sense switch" (also referred to as "cassette switch" or just "tape sense") 
     * is an internal physical switch within the VIC-20's dedicated tape drive. This switch
     * detects whether a button (Play/FF/Rew) has been pressed on the unit, signalling to the 
     * computer's CPU that the user is attempting to load or save data.
     * 
     * Under normal conditions, the sensing line (Pin 6 of the cassette port) is pulled high 
     * by a resistor inside the computer. When you press Play, Rewind, or Fast Forward, a 
     * physical switch inside the Datasette closes, connecting this line directly to ground.
     * 
     * The VIC-20's KERNAL ROM continuously monitors this line. When it detects the transition
     * to a low state, it confirms that a button is engaged and subsequently activates the motor
     * control circuit to start the tape.
     * 
     * Connected to Pin 6 of VIA #1's port A.
     */
    private boolean cassetteSwitchSense;
    
    /**
     * The motor is controlled by the CA2 line of VIA #1. By toggling this line, the software 
     * can programmatically start or stop tape movement.
     * 
     * When the motor is off, the CA2 line is held high (+5V), which cuts off the transistor 
     * switch and removes power from the motor pin. The CA2 output from the VIA is active-low
     * for motor activation. When the computer software (KERNAL) wants to start the tape, it 
     * sets this line to a low voltage (0V).
     */
    private boolean cassetteMotorControl;
    
    /**
     * The value to increment the current time by each cpu clock cycle.
     */
    private int stepSpeed;
    
    /**
     * The current "time" within the tape data. This time is in cpu clock cycles. It is 
     * incremented each cpu clock cycle by the step speed above.
     */
    private int currentTime;
    
    /**
     * The "time" at which the next pulse will occur.
     */
    private int timeOfNextPulse;
    
    /**
     * Constructor for C1530Datasette.
     */
    public C1530Datasette() {
        cassetteSwitchSense = true;    // No button pressed (active low).
        cassetteMotorControl = true;   // Motor off (active low).
    }
    
    /**
     * Acts as if a tape has been inserted, using the given tape data byte array
     * for the raw .TAP image.
     * 
     * @param tapeData The raw .TAP tape image data to insert.
     */
    public void insertTape(byte[] tapeData) {
        tape = new TapeImage(tapeData);
        currentTime = 0;
    }
    
    /**
     * Acts as if the tape has been ejected.
     */
    public void ejectTape() {
        tape = null;
    }

    /**
     * Returns true if the motor is currently on; otherwise false.
     * 
     * @return true if the motor is currently on; otherwise false.
     */
    public boolean isMotorOn() {
        return motorOn;
    }
    
    /**
     * Sets the value of the motor control line, which is active low, so a value of false
     * will turn on the motor and true will turn it off.
     * 
     * @param cassetteMotorControl (active low)false to turn on the motor, true to turn it off.
     */
    public void setCassetteMotorControl(boolean cassetteMotorControl) {
        // If the value has changed, then update the motor state.
        if (cassetteMotorControl ^ this.cassetteMotorControl) {
            // Remember, this is active low, so false means motor on.
            this.cassetteMotorControl = cassetteMotorControl;
            updateMotor();
        }
    }
    
    /**
     * Updates the motor status based on the motor control and cassette switch state.
     */
    private void updateMotor() {
        if (!cassetteMotorControl && !cassetteSwitchSense) {
            motorOn = true;
            motorTimeToLive = 0;
        } else if (motorOn) {
            motorTimeToLive = 32000;
        } 
    }

    /**
     * Gets the current cassette switch state.
     * 
     * @return the current cassette switch state.
     */
    public boolean getCassetteSwitchSense() {
        // Remember, this is active low, so false means a button is currently pressed.
        return cassetteSwitchSense;
    }
    
    /**
     * Used internally to update the cassette switch sense, updating the motor on/off
     * start as a consequence, if required.
     * 
     * @param cassetteSwitchSense
     */
    private void setCassetteSwitchSense(boolean cassetteSwitchSense) {
        this.cassetteSwitchSense = cassetteSwitchSense;
        updateMotor();
    }
    
    /**
     * This is called once per CPU cycle, as part of the VIA #2 cycle emulation. It
     * increments the current time, and if it reaches the next time, then reads in 
     * the next time after that and returns true. Otherwise, if the current time is
     * still before the next time, then it returns false. The net effect is that it 
     * returns a true pulse for every falling edge in the incoming data.
     * 
     * @return true if it is time for a pulse (on VIA#1 CA1); otherwise false.
     */
    public boolean getPulse() {
        // Has the end of tape data or motor TTL been reached?
        if ((motorTimeToLive > 0) && ((--motorTimeToLive == 0) || tape.isEndOfInput())) {
            motorOn = false;
            motorTimeToLive = 0;
            return false;
        }
        
        currentTime += stepSpeed;
        
        // Check if the time of the next pulse has been reached.
        if ((currentTime - timeOfNextPulse) >= 0) {
            // It has been reached, so if it is not the end of the tape...
            if (!tape.isEndOfInput()) {
                // ...then get the time (i.e. cpu cycles) to next pulse.
                timeOfNextPulse += getTimeToNextPulse();
                return true;
            } else {
                // Otherwise stop the tape.
                stop();
                stepSpeed = 0;
                return false;
            }
        } else {
            // No pulse this cycle.
            return false;
        }
    }
    
    /**
     * Emulates the PLAY button being pressed.
     */
    public void play() {
        if (!tape.isEndOfInput()) {
            setCassetteSwitchSense(false);
        }
        stepSpeed = 1;
    }
    
    /**
     * Emulates the STOP button being pressed.
     */
    public void stop() {
        // If PLAY button is down (only PLAY button is supported), then turn it off.
        if (!cassetteSwitchSense) {
            setCassetteSwitchSense(true);
        }
    }
    
    /**
     * Gets the number of CPU cycles until the next falling edge.
     * 
     * @return the number of CPU cycles until the next falling edge.
     */
    private int getTimeToNextPulse() {
        // If end of tape data has been reached and PLAY button is down, then automatically STOP.
        if (tape.isEndOfInput() && !cassetteSwitchSense) {
            stop();
        }
        return tape.getNumOfCyclesToNextPulse();
    }
}
