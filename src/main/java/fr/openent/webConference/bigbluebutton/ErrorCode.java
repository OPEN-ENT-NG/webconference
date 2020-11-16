package fr.openent.webConference.bigbluebutton;

import java.util.HashMap;
import java.util.Map;

public enum ErrorCode {
    TOO_MANY_SESSIONS_PER_STRUCTURE("tooManySessionsPerStructure"), TOO_MANY_USERS("tooManyUsers"), TOO_MANY_SESSIONS("tooManySessions");

    private final String code;

    private static final Map<String, ErrorCode> lookup = new HashMap<>();

    static {
        for (ErrorCode errorCode : ErrorCode.values()) {
            lookup.put(errorCode.code(), errorCode);
        }
    }

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return this.code;
    }

    public static ErrorCode get(String code) {
        return lookup.get(code);
    }
}
