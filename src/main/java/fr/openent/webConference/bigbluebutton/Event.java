package fr.openent.webConference.bigbluebutton;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

public class Event {
    private JsonObject event;
    private String timestamp;

    public Event(String value) {
        Map<String, List<String>> decodedParams = new QueryStringDecoder("?" + value).parameters();
        event = new JsonArray(decodedParams.get("event").get(0)).getJsonObject(0).getJsonObject("data");
        timestamp = decodedParams.get("timestamp").get(0);
    }

    public JsonObject getEvent() {
        return this.event;
    }

    public String getType() {
        return this.event.getString("id");
    }

    public String getMeetingId() {
        return this.event.getJsonObject("attributes").getJsonObject("meeting").getString("external-meeting-id");
    }

    public boolean isMeetingEnded() {
        return "meeting-ended".equals(this.getType());
    }
}
