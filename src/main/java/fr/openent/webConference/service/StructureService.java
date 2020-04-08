package fr.openent.webConference.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface StructureService {

    void retrieveUAI(String id, Handler<Either<String, JsonObject>> handler);

    void retrieveActiveUsers(String id, Handler<Either<String, JsonArray>> handler);
}
