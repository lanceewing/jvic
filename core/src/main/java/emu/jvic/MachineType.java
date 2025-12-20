package emu.jvic;

/**
 * An enum that represents the two types of VIC 20 machine, i.e. PAL and NTSC.
 * 
 * @author Lance Ewing
 */
public enum MachineType {

    PAL(1108405, 284, 312, 220, 272, 55, 34, 50),

    NTSC(1022727, 260, 263, 199, 252, 49, 8, 60);

    private int cyclesPerSecond;
    private int totalScreenWidth;
    private int totalScreenHeight;
    private int visibleScreenWidth;
    private int visibleScreenHeight;
    private int horizontalOffset;
    private int verticalOffset;
    private int framesPerSecond;
    private int frameDuration;
    private int cyclesPerFrame;
    private int cyclesPerLine;

    /**
     * Constructor for MachineType.
     * 
     * @param cyclesPerSecond
     * @param totalScreenWidth
     * @param totalScreenHeight
     * @param visibleScreenWidth
     * @param visibleScreenHeight
     * @param horizontalOffset
     * @param verticalOffset
     * @param framesPerSecond
     */
    MachineType(int cyclesPerSecond, int totalScreenWidth, int totalScreenHeight, int visibleScreenWidth,
            int visibleScreenHeight, int horizontalOffset, int verticalOffset, int framesPerSecond) {
        this.cyclesPerSecond = cyclesPerSecond;
        this.totalScreenWidth = totalScreenWidth;
        this.totalScreenHeight = totalScreenHeight;
        this.visibleScreenWidth = visibleScreenWidth;
        this.visibleScreenHeight = visibleScreenHeight;
        this.horizontalOffset = horizontalOffset;
        this.verticalOffset = verticalOffset;
        this.framesPerSecond = framesPerSecond;
        this.frameDuration = 1000 / framesPerSecond;
        this.cyclesPerFrame = cyclesPerSecond / framesPerSecond;
        this.cyclesPerLine = totalScreenWidth / 4;
    }

    /**
     * @return the cyclesPerLine
     */
    public int getCyclesPerLine() {
        return cyclesPerLine;
    }

    /**
     * @return The last line.
     */
    public int getLastLine() {
        return totalScreenHeight - 1;
    }

    /**
     * @return the cyclesPerSecond
     */
    public int getCyclesPerSecond() {
        return cyclesPerSecond;
    }

    /**
     * @return the totalScreenWidth
     */
    public int getTotalScreenWidth() {
        return totalScreenWidth;
    }

    /**
     * @return the totalScreenHeight
     */
    public int getTotalScreenHeight() {
        return totalScreenHeight;
    }

    /**
     * @return the visibleScreenWidth
     */
    public int getVisibleScreenWidth() {
        return visibleScreenWidth;
    }

    /**
     * @return the visibleScreenHeight
     */
    public int getVisibleScreenHeight() {
        return visibleScreenHeight;
    }

    /**
     * @return the horizontalOffset
     */
    public int getHorizontalOffset() {
        return horizontalOffset;
    }

    /**
     * @return the verticalOffset
     */
    public int getVerticalOffset() {
        return verticalOffset;
    }

    /**
     * @return the framesPerSecond
     */
    public int getFramesPerSecond() {
        return framesPerSecond;
    }

    /**
     * @return the frameDuration
     */
    public int getFrameDuration() {
        return frameDuration;
    }

    /**
     * @return the cyclesPerFrame
     */
    public int getCyclesPerFrame() {
        return cyclesPerFrame;
    }

    public boolean isPAL() {
        return equals(PAL);
    }

    public boolean isNTSC() {
        return equals(NTSC);
    }
}
