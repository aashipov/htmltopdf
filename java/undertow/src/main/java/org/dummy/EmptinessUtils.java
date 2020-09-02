package org.dummy;

/**
 * If {@link String} is null or empty.
 */
public final class EmptinessUtils {

    private EmptinessUtils() {
        //Utility
    }

    /**
     * Is {@link CharSequence} blank?.
     * @param cs {@link CharSequence}
     * @return is blank?
     */
    public static boolean isBlank(final CharSequence cs) {
        if (null != cs) {
            final int strLen = cs.length();
            if (0 != strLen) {
                for (int i = 0; i < strLen; i++) {
                    if (!Character.isWhitespace(cs.charAt(i))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
