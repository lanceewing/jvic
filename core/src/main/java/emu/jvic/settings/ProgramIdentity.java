package emu.jvic.settings;

import emu.jvic.config.AppConfigItem;

/**
 * Works out the name that identifies a program for its quick save slot and its
 * per-program settings, so that the two always agree.
 */
public final class ProgramIdentity {

    private ProgramIdentity() {
    }

    /**
     * Returns the program's name before it is turned into a slug: its name, or for
     * programs the user supplied (which are all called {@link AppConfigItem#ADHOC_NAME})
     * "adhoc " followed by the file name.
     *
     * @param program A program's configuration. May be null.
     *
     * @return The name, or null if there is nothing to go on.
     */
    public static String keyName(AppConfigItem program) {
        String name = (program != null) ? program.getName() : null;
        if ((name == null) || name.trim().isEmpty() || AppConfigItem.ADHOC_NAME.equals(name)) {
            String filePath = (program != null) ? program.getFilePath() : null;
            if ((filePath != null) && !filePath.trim().isEmpty()) {
                int separator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
                name = "adhoc " + filePath.substring(separator + 1);
            }
        }
        return name;
    }

    /**
     * Turns a slug of {@link #keyName(AppConfigItem)} into the program key.
     *
     * @param slug The slug, from {@code JVicRunner.slugify}. May be null.
     *
     * @return The key, which is "default" if the slug is empty.
     */
    public static String key(String slug) {
        return ((slug == null) || slug.isEmpty()) ? "default" : slug;
    }
}
