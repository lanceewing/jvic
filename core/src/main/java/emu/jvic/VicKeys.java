package emu.jvic;

import com.badlogic.gdx.Input.Keys;

public interface VicKeys {

    //    7   6   5   4   3   2   1   0
    //   --------------------------------
    // 7| F7  F5  F3  F1  CDN CRT RET DEL    CRT=Cursor-Right, CDN=Cursor-Down
    //  |
    // 6| HOM UA  =   RSH /   ;   *   BP     BP=British Pound, RSH=Should be Right-SHIFT,
    //  |                                    UA=Up Arrow
    // 5| -   @   :   .   ,   L   P   +
    //  |
    // 4| 0   O   K   M   N   J   I   9
    //  |
    // 3| 8   U   H   B   V   G   Y   7
    //  |
    // 2| 6   T   F   C   X   D   R   5
    //  |
    // 1| 4   E   S   Z   LSH A   W   3      LSH=Should be Left-SHIFT
    //  |
    // 0| 2   Q   CBM SPC STP CTL LA  1      LA=Left Arrow, CTL=Should be CTRL, STP=RUN/STOP
    //  |                                    CBM=Commodore key
    
    public static final int F7 = 1;
    public static final int F5 = 2;
    public static final int F3 = 3;
    public static final int F1 = 4;
    public static final int CURSOR_DOWN = 5;
    public static final int CURSOR_RIGHT = 6;
    public static final int RETURN = 7;
    public static final int DELETE = 8;
    
    public static final int HOME = 9;
    public static final int UP_ARROW = 10;
    public static final int EQUALS = 11;
    public static final int RIGHT_SHIFT = 12;
    public static final int FORWARD_SLASH = 13;
    public static final int SEMI_COLON = 14;
    public static final int ASTERISK = 15;
    public static final int POUND = 16;
    
    public static final int HYPHEN = 17;
    public static final int AT = 18;
    public static final int COLON = 19;
    public static final int PERIOD = 20;
    public static final int COMMA = 21;
    public static final int L = 22;
    public static final int P = 23;
    public static final int PLUS =24;
    
    public static final int ZERO = 25;
    public static final int O = 26;
    public static final int K = 27;
    public static final int M = 28;
    public static final int N = 29;
    public static final int J = 30;
    public static final int I = 31;
    public static final int NINE = 32;
    
    public static final int EIGHT = 33;
    public static final int U = 34;
    public static final int H = 35;
    public static final int B = 36;
    public static final int V = 37;
    public static final int G = 38;
    public static final int Y = 39;
    public static final int SEVEN = 40;
    
    public static final int SIX = 41;
    public static final int T = 42;
    public static final int F = 43;
    public static final int C = 44;
    public static final int X = 45;
    public static final int D = 46;
    public static final int R = 47;
    public static final int FIVE = 48;
    
    public static final int FOUR = 49;
    public static final int E = 50;
    public static final int S = 51;
    public static final int Z = 52;
    public static final int LEFT_SHIFT = 53;
    public static final int A = 54;
    public static final int W = 55;
    public static final int THREE = 56;
    
    public static final int TWO = 57;
    public static final int Q = 58;
    public static final int CBM = 59;
    public static final int SPACE = 60;
    public static final int RUN_STOP = 61;
    public static final int CONTROL = 62;
    public static final int LEFT_ARROW = 63;
    public static final int ONE = 64;
    
    /**
     * Mapping between typed characters and the equivalent VIC keyboard key combinations.
     */
    public static final int[][] VIC_CHAR_MAP = new int[][] {
        { '`', VicKeys.LEFT_ARROW },
        { '1', VicKeys.ONE },
        { '!', VicKeys.LEFT_SHIFT, VicKeys.ONE },
        { '2', VicKeys.TWO },
        { '"', VicKeys.LEFT_SHIFT, VicKeys.TWO },
        { '3', VicKeys.THREE },
        { '£', VicKeys.POUND },
        { '4', VicKeys.FOUR },
        { '$', VicKeys.LEFT_SHIFT, VicKeys.FOUR },
        { '5', VicKeys.FIVE },
        { '%', VicKeys.LEFT_SHIFT, VicKeys.FIVE },
        { '6', VicKeys.SIX },
        { '^', VicKeys.SIX },
        { '7', VicKeys.SEVEN },
        { '&', VicKeys.LEFT_SHIFT, VicKeys.SIX },
        { '8', VicKeys.EIGHT },
        { '*', VicKeys.ASTERISK },
        { '\'', VicKeys.LEFT_SHIFT, VicKeys.SEVEN },
        { '9', VicKeys.NINE },
        { '(', VicKeys.LEFT_SHIFT, VicKeys.EIGHT },
        { ')', VicKeys.LEFT_SHIFT, VicKeys.NINE },
        { '0', VicKeys.ZERO },
        { '-', VicKeys.HYPHEN },
        { '_', VicKeys.LEFT_SHIFT, VicKeys.AT },
        { '+', VicKeys.PLUS },
        { '=', VicKeys.EQUALS },
        { 'Q', VicKeys.Q },
        { 'q', VicKeys.Q },
        { 'W', VicKeys.W },
        { 'w', VicKeys.W },
        { 'E', VicKeys.E },
        { 'e', VicKeys.E },
        { 'R', VicKeys.R },
        { 'r', VicKeys.R },
        { 'T', VicKeys.T },
        { 't', VicKeys.T },
        { 'Y', VicKeys.Y },
        { 'y', VicKeys.Y },
        { 'U', VicKeys.U },
        { 'u', VicKeys.U },
        { 'I', VicKeys.I },
        { 'i', VicKeys.I },
        { 'O', VicKeys.O },
        { 'o', VicKeys.O },
        { 'P', VicKeys.P },
        { 'p', VicKeys.P },
        { '[', VicKeys.LEFT_SHIFT, VicKeys.COLON },
        { ']', VicKeys.LEFT_SHIFT, VicKeys.SEMI_COLON },
        { '{', VicKeys.LEFT_SHIFT, VicKeys.COLON },
        { '}', VicKeys.LEFT_SHIFT, VicKeys.SEMI_COLON },
        { 'A', VicKeys.A },
        { 'a', VicKeys.A },
        { 'S', VicKeys.S },
        { 's', VicKeys.S },
        { 'D', VicKeys.D },
        { 'd', VicKeys.D },
        { 'F', VicKeys.F },
        { 'f', VicKeys.F },
        { 'G', VicKeys.G },
        { 'g', VicKeys.G },
        { 'H', VicKeys.H },
        { 'h', VicKeys.H },
        { 'J', VicKeys.J },
        { 'j', VicKeys.J },
        { 'K', VicKeys.K },
        { 'k', VicKeys.K },
        { 'L', VicKeys.L },
        { 'l', VicKeys.L },
        { ';', VicKeys.SEMI_COLON },
        { ':', VicKeys.COLON },
        { '@', VicKeys.AT },
        { '#', VicKeys.LEFT_SHIFT, VicKeys.THREE },
        { '\\', VicKeys.UP_ARROW },
        { '|', VicKeys.LEFT_SHIFT, VicKeys.UP_ARROW },
        { 'Z', VicKeys.Z },
        { 'z', VicKeys.Z },
        { 'X', VicKeys.X },
        { 'x', VicKeys.X },
        { 'C', VicKeys.C },
        { 'c', VicKeys.C },
        { 'V', VicKeys.V },
        { 'v', VicKeys.V },
        { 'B', VicKeys.B },
        { 'b', VicKeys.B },
        { 'N', VicKeys.N },
        { 'n', VicKeys.N },
        { 'M', VicKeys.M },
        { 'm', VicKeys.M },
        { ',', VicKeys.COMMA },
        { '<', VicKeys.LEFT_SHIFT, VicKeys.COMMA },
        { '.', VicKeys.PERIOD },
        { '>', VicKeys.LEFT_SHIFT, VicKeys.PERIOD },
        { '/', VicKeys.FORWARD_SLASH },
        { '?', VicKeys.LEFT_SHIFT, VicKeys.FORWARD_SLASH },
        { ' ', VicKeys.SPACE }
    };
    
    /**
     * Maps the remaining keys, i.e. those not handles by character mapping above, by
     * mapping the libgdx keycode to the equivalent VIC keyboard key combinations.
     */
    public static final int[][] VIC_KEY_MAP = new int[][] {
        { Keys.DEL, VicKeys.DELETE },
        { Keys.BACKSPACE, VicKeys.DELETE },
        { Keys.TAB, VicKeys.CBM },
        { Keys.ENTER, VicKeys.RETURN },
        { Keys.SHIFT_LEFT, VicKeys.LEFT_SHIFT },
        { Keys.SHIFT_RIGHT, VicKeys.RIGHT_SHIFT },
        { Keys.CONTROL_LEFT, VicKeys.CONTROL },
        { Keys.LEFT, VicKeys.LEFT_SHIFT, VicKeys.CURSOR_RIGHT },
        { Keys.UP, VicKeys.LEFT_SHIFT, VicKeys.CURSOR_DOWN },
        { Keys.DOWN, VicKeys.CURSOR_DOWN },
        { Keys.RIGHT, VicKeys.CURSOR_RIGHT },
        { Keys.HOME, VicKeys.HOME },
        { Keys.INSERT, VicKeys.LEFT_SHIFT, VicKeys.DELETE },
        { Keys.ESCAPE, VicKeys.RUN_STOP },
        { Keys.F1, VicKeys.F1 },
        { Keys.F2, VicKeys.LEFT_SHIFT, VicKeys.F1 },
        { Keys.F3, VicKeys.F3 },
        { Keys.F4, VicKeys.LEFT_SHIFT, VicKeys.F3 },
        { Keys.F5, VicKeys.F5 },
        { Keys.F6, VicKeys.LEFT_SHIFT, VicKeys.F5 },
        { Keys.F7, VicKeys.F7 },
        { Keys.F8, VicKeys.LEFT_SHIFT, VicKeys.F7 },
    };
}
