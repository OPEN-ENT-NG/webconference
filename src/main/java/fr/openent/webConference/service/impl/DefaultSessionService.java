package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.service.GuestLinkService;
import fr.openent.webConference.service.SessionService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

public class DefaultSessionService implements SessionService {
    private static final Logger log = LoggerFactory.getLogger(DefaultSessionService.class);
    private final GuestLinkService guestLinkService = new DefaultGuestLinkService();

    @Override
    public void create(String sessionId, String roomId, String internalId, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + WebConference.SESSION_TABLE + " (id, internal_id, room_id) VALUES (?, ?, ?) RETURNING *;";
        JsonArray params = new JsonArray()
                .add(sessionId)
                .add(internalId)
                .add(roomId);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void end(String sessionId, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + WebConference.SESSION_TABLE + " SET end_date = now(), end_time = now() WHERE id = ?;";
        JsonArray params = new JsonArray().add(sessionId);

        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(result -> {
            if (result.isLeft()) {
                handler.handle(result);
                return;
            }
            guestLinkService.renewForEndedSession(sessionId)
                    .onFailure(err -> log.error("[WebConference@DefaultSessionService::end] Failed to renew guest link of session " + sessionId, err))
                    .onComplete(ignored -> handler.handle(result));
        }));
    }
}
