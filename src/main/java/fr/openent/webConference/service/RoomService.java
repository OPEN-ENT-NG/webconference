package fr.openent.webConference.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public interface RoomService {
    void list(UserInfos user, Handler<Either<String, JsonArray>> handler);

    void get(String id, Handler<Either<String, JsonObject>> handler);

    void create(String referer, JsonObject room, UserInfos user, Handler<Either<String, JsonObject>> handler);

    void update(String id, JsonObject room, Handler<Either<String, JsonObject>> handler);

    void delete(String id, Handler<Either<String, JsonObject>> handler);

    void setStructure(String id, String structureId, Handler<Either<String, JsonObject>> handler);
}
