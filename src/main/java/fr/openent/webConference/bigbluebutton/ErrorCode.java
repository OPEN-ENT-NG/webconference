package fr.openent.webConference.bigbluebutton;

public enum ErrorCode {
    TOO_MANY_ROOMS("tooManyRooms");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}
