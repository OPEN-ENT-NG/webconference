package fr.openent.webConference.service;

import fr.openent.webConference.tiers.RoomProvider;
import io.vertx.core.Future;

public interface GuestLinkService {
    Future<Void> bindToSession(String roomId, String sessionId);

    Future<Void> renewForEndedSession(String sessionId);

    Future<Void> renewIfEnded(String roomId, String sessionId, RoomProvider provider);
}
