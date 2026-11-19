package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.service.GuestLinkService;
import fr.openent.webConference.tiers.MeetingStatus;
import fr.openent.webConference.tiers.RoomProvider;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
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
                " SET guest_token = CASE WHEN guest_session IS NULL THEN guest_token ELSE ? END, guest_session = ?" +
                " WHERE id = ? AND guest_link_enabled = TRUE AND guest_token IS NOT NULL;";
        JsonArray params = new JsonArray().add(generateToken()).add(sessionId).add(roomId);
        return executeUpdate(query, params);
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
