package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.core.constants.Field;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.StructureService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.UUID;

public class DefaultRoomService implements RoomService {
    private StructureService structureService = new DefaultStructureService();

    @Override
    public void list(List<String> groupsAndUserIds, UserInfos user, Handler<Either<String, JsonArray>> handler) {
        StringBuilder query = new StringBuilder();
        JsonArray params = new JsonArray();

        query.append("SELECT r.id, name, owner AS owner_id, sessions, link, public_link, allow_waiting_room, allow_streaming, streaming_link, streaming_key, active_session, structure, collab, opener ")
                .append("FROM ").append(WebConference.ROOM_TABLE).append(" r ")
                .append("LEFT JOIN ").append(WebConference.ROOM_SHARES_TABLE).append(" rs ON r.id = rs.resource_id ")
                .append("LEFT JOIN ").append(WebConference.MEMBERS_TABLE).append(" m ON (m.id = rs.member_id AND m.group_id IS NOT NULL) ")
                .append("WHERE (rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds)).append(" AND rs.action IN (?, ?)) ")
                .append("OR r.owner = ? ")
                .append("GROUP BY r.id ")
                .append("ORDER BY r.name;");

        for (String groupOrUser : groupsAndUserIds) {
            params.add(groupOrUser);
        }
        params.add(WebConference.MANAGER_SHARING_BEHAVIOUR).add(WebConference.CONTRIB_SHARING_BEHAVIOUR).add(user.getUserId());

        Sql.getInstance().prepared(query.toString(), params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void get(String id, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT * FROM " + WebConference.ROOM_TABLE + " WHERE id = ?;";
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
    public void create(String referer, JsonObject room, boolean isPublic, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        String id = UUID.randomUUID().toString();
        String moderatorPW = UUID.randomUUID().toString();
        String attendeePW = UUID.randomUUID().toString();
        String link = referer + "/rooms/" + id + "/join";
        String public_link = isPublic ? (WebConference.publicUrl + id) : null;
        String query = "INSERT INTO " + WebConference.ROOM_TABLE +
                " (id, name, owner, moderator_pw, attendee_pw, link, public_link, allow_waiting_room, allow_streaming," +
                " streaming_link, streaming_key, structure)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                " RETURNING *;";
        JsonArray params = new JsonArray()
                .add(id)
                .add(room.getString(Field.NAME, ""))
                .add(user.getUserId() != null ? user.getUserId() : "")
                .add(moderatorPW)
                .add(attendeePW)
                .add(link)
                .add(public_link)
                .add(room.getBoolean(Field.ALLOW_WAITING_ROOM))
                .add(room.getBoolean(Field.ALLOW_STREAMING))
                .add(room.getString(Field.STREAMING_LINK))
                .add(room.getString(Field.STREAMING_KEY))
                .add(room.getString(Field.STRUCTURE, ""));

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void update(String id, JsonObject room, boolean isPublic, Handler<Either<String, JsonObject>> handler) {
        String public_link = isPublic ? (WebConference.publicUrl + id) : null;
        String query = "UPDATE " + WebConference.ROOM_TABLE +
                " SET name = ?, structure = ?, collab = ?, opener = ?, public_link = ?, allow_waiting_room = ?," +
                " allow_streaming = ?, streaming_link = ?, streaming_key = ?" +
                " WHERE id = ?" +
                " RETURNING *;";
        JsonArray params = new JsonArray()
                .add(room.getString("name"))
                .add(room.getString("structure"))
                .add(room.getBoolean("collab", false))
                .add(room.getString("opener", null))
                .add(public_link)
                .add(room.getBoolean("allow_waiting_room"))
                .add(room.getBoolean("allow_streaming", false))
                .add(room.getString("streaming_link", null))
                .add(room.getString("streaming_key", null))
                .add(id);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(String id, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + WebConference.ROOM_TABLE + " WHERE id = ?;";
        JsonArray params = new JsonArray().add(id);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void setStructure(String id, String structureId, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + WebConference.ROOM_TABLE + " SET structure = ? WHERE id = ? RETURNING *";
        JsonArray params = new JsonArray()
                .add(structureId)
                .add(id);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(evt -> this.get(id, handler)));
    }

    @Override
    public void getSharedWithMe(String roomId, UserInfos user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + WebConference.ROOM_SHARES_TABLE + " WHERE resource_id = ? AND member_id = ?;";
        JsonArray params = new JsonArray().add(roomId).add(user.getUserId());
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void getUsersShared(String roomId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT member_id FROM " + WebConference.ROOM_SHARES_TABLE + " WHERE resource_id = ? AND action IN (?,?);";
        JsonArray params = new JsonArray().add(roomId).add(WebConference.MANAGER_SHARING_BEHAVIOUR).add(WebConference.CONTRIB_SHARING_BEHAVIOUR);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void getAllMyRoomRights(List<String> groupsAndUserIds, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT resource_id, action FROM " + WebConference.ROOM_SHARES_TABLE +
                " WHERE member_id IN " + Sql.listPrepared(groupsAndUserIds) + " AND action IN (?,?);";
        JsonArray params = new JsonArray()
                .addAll(new fr.wseduc.webutils.collections.JsonArray(groupsAndUserIds))
                .add(WebConference.CONTRIB_SHARING_BEHAVIOUR)
                .add(WebConference.MANAGER_SHARING_BEHAVIOUR);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }
}
