package fr.openent.webConference.service;

import fr.openent.webConference.tiers.RoomProvider;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface GuestLinkService {
    Future<Void> bindToSession(String roomId, String sessionId);

    Future<Void> bindToRunningSession(JsonObject room, RoomProvider provider);

    Future<Void> renewForEndedSession(String sessionId);

    Future<Void> renewIfEnded(String roomId, String sessionId, RoomProvider provider);

    Future<JsonObject> findRoomByToken(String token);

    Future<GuestLinkState> resolveState(JsonObject room, RoomProvider provider);

    Future<JsonObject> setEnabled(String roomId, boolean enabled);
}
