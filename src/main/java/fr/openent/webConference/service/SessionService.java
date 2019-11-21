package fr.openent.webConference.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface SessionService {

    void create(String sessionId, String roomId, String internalId, Handler<Either<String, JsonObject>> handler);
}
