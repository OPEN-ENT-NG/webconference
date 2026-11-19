package fr.openent.webConference.tiers;

public enum MeetingStatus {
    RUNNING,
    // Created but empty: nobody has joined yet, or everyone left a moment ago.
    NOT_RUNNING,
    ENDED
}
