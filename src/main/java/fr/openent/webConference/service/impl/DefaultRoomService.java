package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.service.RoomService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

import java.util.UUID;

public class DefaultRoomService implements RoomService {
    private String host;

    public DefaultRoomService(String host) {
        this.host = host;
    }

    @Override
    public void list(UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT id, name, sessions, link FROM " + WebConference.DB_SCHEMA + ".room WHERE owner = ?";
        JsonArray params = new JsonArray().add(user.getUserId());
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void create(JsonObject room, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String id = UUID.randomUUID().toString();
        String moderatorPW = UUID.randomUUID().toString();
        String attendeePW = UUID.randomUUID().toString();
        String link = this.host + "/webconference/rooms/" + id + "/join";
        String query = "INSERT INTO " + WebConference.DB_SCHEMA + ".room(id, name, owner, moderator_pw, attendee_pw, link) VALUES (?, ?, ?, ?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(id)
                .add(room.getString("name"))
                .add(user.getUserId())
                .add(moderatorPW)
                .add(attendeePW)
                .add(link);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void update(String id, JsonObject room, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + WebConference.DB_SCHEMA + ".room SET name=? WHERE id = ? RETURNING *;";
        JsonArray params = new JsonArray()
                .add(room.getString("name"))
                .add(id);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(String id, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + WebConference.DB_SCHEMA + ".room WHERE id = ?;";
        JsonArray params = new JsonArray().add(id);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }
}
