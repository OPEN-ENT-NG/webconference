package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.core.constants.Field;
import fr.openent.webConference.service.GuestLinkService;
import fr.openent.webConference.service.GuestLinkState;
import fr.openent.webConference.tiers.MeetingStatus;
import fr.openent.webConference.tiers.RoomProvider;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.security.SecureRandom;
import java.util.Base64;

public class DefaultGuestLinkService implements GuestLinkService {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public Future<Void> bindToSession(String roomId, String sessionId) {
        // A link still bound to an older session missed its renewal: it must not open the new session.
        String query = "UPDATE " + WebConference.ROOM_TABLE +
                " SET guest_token = CASE WHEN guest_session IS NULL OR guest_session = ? THEN guest_token ELSE ? END," +
                " guest_session = ?" +
                " WHERE id = ? AND guest_link_enabled = TRUE AND guest_token IS NOT NULL;";
        JsonArray params = new JsonArray().add(sessionId).add(generateToken()).add(sessionId).add(roomId);
        return executeUpdate(query, params);
    }

    @Override
    public Future<Void> bindToRunningSession(JsonObject room, RoomProvider provider) {
        String activeSession = room.getString(Field.ACTIVE_SESSION);
        if (!room.getBoolean(Field.GUEST_LINK_ENABLED, false) || room.getString(Field.GUEST_SESSION) != null || activeSession == null) {
            return Future.succeededFuture();
        }
        return provider.getMeetingStatus(activeSession)
                .compose(status -> status == MeetingStatus.RUNNING
                        ? bindToSession(room.getString("id"), activeSession)
                        : Future.<Void>succeededFuture());
    }

    @Override
    public Future<Void> renewForEndedSession(String sessionId) {
        String query = "UPDATE " + WebConference.ROOM_TABLE +
                " SET guest_token = ?, guest_session = NULL" +
                " WHERE guest_session = ?;";
        JsonArray params = new JsonArray().add(generateToken()).add(sessionId);
        return executeUpdate(query, params);
    }

    @Override
    public Future<Void> renewIfEnded(String roomId, String sessionId, RoomProvider provider) {
        return isBoundTo(roomId, sessionId).compose(bound -> {
            if (!bound) {
                return Future.succeededFuture();
            }
            return provider.getMeetingStatus(sessionId)
                    .compose(status -> status == MeetingStatus.ENDED
                            ? renewForEndedSession(sessionId)
                            : Future.<Void>succeededFuture());
        });
    }

    @Override
    public Future<JsonObject> findRoomByToken(String token) {
        Promise<JsonObject> promise = Promise.promise();
        String query = "SELECT id, name, structure, attendee_pw, active_session, guest_session FROM " + WebConference.ROOM_TABLE +
                " WHERE guest_token = ? AND guest_link_enabled = TRUE;";
        Sql.getInstance().prepared(query, new JsonArray().add(token), SqlResult.validResultHandler(result -> {
            if (result.isLeft()) {
                promise.fail(result.left().getValue());
            } else {
                JsonArray rooms = result.right().getValue();
                promise.complete(rooms.isEmpty() ? null : rooms.getJsonObject(0));
            }
        }));
        return promise.future();
    }

    @Override
    public Future<GuestLinkState> resolveState(JsonObject room, RoomProvider provider) {
        String boundSession = room.getString(Field.GUEST_SESSION);
        if (boundSession == null) {
            return Future.succeededFuture(GuestLinkState.WAITING);
        }
        if (!boundSession.equals(room.getString(Field.ACTIVE_SESSION))) {
            return expire(boundSession);
        }
        return provider.getMeetingStatus(boundSession).compose(status -> {
            if (status == MeetingStatus.RUNNING) {
                return Future.succeededFuture(GuestLinkState.OPEN);
            }
            if (status == MeetingStatus.NOT_RUNNING) {
                return Future.succeededFuture(GuestLinkState.WAITING);
            }
            return expire(boundSession);
        });
    }

    @Override
    public Future<JsonObject> setEnabled(String roomId, boolean enabled) {
        String query = enabled
                ? "UPDATE " + WebConference.ROOM_TABLE + " SET guest_link_enabled = TRUE, guest_token = COALESCE(guest_token, ?) WHERE id = ? RETURNING *;"
                : "UPDATE " + WebConference.ROOM_TABLE + " SET guest_link_enabled = FALSE, guest_token = NULL, guest_session = NULL WHERE id = ? RETURNING *;";
        JsonArray params = enabled ? new JsonArray().add(generateToken()).add(roomId) : new JsonArray().add(roomId);

        Promise<JsonObject> promise = Promise.promise();
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(result -> {
            if (result.isLeft()) {
                promise.fail(result.left().getValue());
            } else {
                promise.complete(result.right().getValue());
            }
        }));
        return promise.future();
    }

    private Future<GuestLinkState> expire(String endedSession) {
        return renewForEndedSession(endedSession).map(GuestLinkState.EXPIRED);
    }

    private Future<Boolean> isBoundTo(String roomId, String sessionId) {
        Promise<Boolean> promise = Promise.promise();
        String query = "SELECT 1 FROM " + WebConference.ROOM_TABLE + " WHERE id = ? AND guest_session = ?;";
        JsonArray params = new JsonArray().add(roomId).add(sessionId);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(result -> {
            if (result.isLeft()) {
                promise.fail(result.left().getValue());
            } else {
                promise.complete(!result.right().getValue().isEmpty());
            }
        }));
        return promise.future();
    }

    private Future<Void> executeUpdate(String query, JsonArray params) {
        Promise<Void> promise = Promise.promise();
        Sql.getInstance().prepared(query, params, SqlResult.validRowsResultHandler(result -> {
            if (result.isLeft()) {
                promise.fail(result.left().getValue());
            } else {
                promise.complete();
            }
        }));
        return promise.future();
    }
}
