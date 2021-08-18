package fr.openent.webConference.config;

public class PublicConf {
    private Boolean allowed = true;
    private String host;

    public PublicConf() {
        this.allowed = allowed;
    }

    public PublicConf allowed(Boolean allowed) {
        this.allowed = allowed;
        return this;
    }

    public Boolean allowed() {
        return this.allowed;
    }

    public PublicConf setHost(String host) {
        this.host = host;
        return this;
    }

    public String host() {
        return this.host;
    }

    public String path() {
        return "/public/rooms/";
    }
}
