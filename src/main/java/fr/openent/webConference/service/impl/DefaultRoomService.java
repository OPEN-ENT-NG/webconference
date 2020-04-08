package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.StructureService;
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
    private StructureService structureService = new DefaultStructureService();

    public DefaultRoomService(String host) {
        this.host = host;
    }

    @Override
    public void list(UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT id, name, sessions, link, active_session, structure FROM " + WebConference.DB_SCHEMA + ".room WHERE owner = ? ORDER BY name";
        JsonArray params = new JsonArray().add(user.getUserId());
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void get(String id, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT id, name, moderator_pw, attendee_pw, active_session, owner, structure FROM " + WebConference.DB_SCHEMA + ".room WHERE id = ?;";
        JsonArray params = new JsonArray().add(id);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(evt -> {
            if (evt.isLeft()) {
                handler.handle(evt.left());
                return;
            }

            JsonObject room = evt.right().getValue();
            if (room.getString("structure") == null) {
                handler.handle(evt.right());
                return;
            }

            String structure = room.getString("structure");
            structureService.retrieveUAI(structure, uai -> {
                if (uai.isLeft()) {
                    handler.handle(uai.left());
                } else {
                    room.put("uai", uai.right().getValue().getString("uai"));
                    handler.handle(new Either.Right<>(room));
                }
            });
        }));
    }

    @Override
    public void create(JsonObject room, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String id = UUID.randomUUID().toString();
        String moderatorPW = UUID.randomUUID().toString();
        String attendeePW = UUID.randomUUID().toString();
        String link = this.host + "/webconference/rooms/" + id + "/join";
        String query = "INSERT INTO " + WebConference.DB_SCHEMA + ".room(id, name, owner, moderator_pw, attendee_pw, link, structure) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(id)
                .add(room.getString("name"))
                .add(user.getUserId())
                .add(moderatorPW)
                .add(attendeePW)
                .add(link)
                .add(room.getString("structure"));

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

    @Override
    public void setStructure(String id, String structureId, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + WebConference.DB_SCHEMA + ".room SET structure = ? WHERE id = ? RETURNING *";
        JsonArray params = new JsonArray()
                .add(structureId)
                .add(id);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(evt -> this.get(id, handler)));
    }
}
