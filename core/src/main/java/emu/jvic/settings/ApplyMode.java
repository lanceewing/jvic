package emu.jvic.settings;

/**
 * When a change to a setting takes effect.
 */
public enum ApplyMode {

    /** Applied as soon as the value changes. */
    LIVE,

    /** Needs the machine to be restarted. */
    RESTART,

    /** Used the next time a program starts. No restart is offered. */
    NEXT_START
}
