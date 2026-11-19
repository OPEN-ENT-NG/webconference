package fr.openent.webConference.helper;

import java.util.regex.Pattern;

public final class GuestLinkHelper {
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final int MAX_NAME_LENGTH = 64;

    private GuestLinkHelper() {
    }

    public static boolean isValidToken(String token) {
        return token != null && TOKEN_FORMAT.matcher(token).matches();
    }

    /** Returns the cleaned guest name, or null when it is empty or longer than 64 characters. */
    public static String sanitizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = CONTROL_CHARS.matcher(rawName).replaceAll("").trim();
        return name.isEmpty() || name.length() > MAX_NAME_LENGTH ? null : name;
    }
}
