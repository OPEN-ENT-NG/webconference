package fr.openent.webConference.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;

public interface RoomService {
    void list(List<String> groupsAndUserIds, UserInfos user, Handler<Either<String, JsonArray>> handler);

    void get(String id, Handler<Either<String, JsonObject>> handler);

    void create(String referer, JsonObject room, boolean isPublic, UserInfos user, Handler<Either<String, JsonObject>> handler);

    void update(String id, JsonObject room, boolean isPublic, Handler<Either<String, JsonObject>> handler);

    void delete(String id, Handler<Either<String, JsonObject>> handler);

    void setStructure(String id, String structureId, Handler<Either<String, JsonObject>> handler);

    void getSharedWithMe(String roomId, UserInfos user, Handler<Either<String, JsonArray>> handler);

    void getUsersShared(String roomId, Handler<Either<String, JsonArray>> handler);

    void getAllMyRoomRights(List<String> groupsAndUserIds, Handler<Either<String, JsonArray>> handler);
}
