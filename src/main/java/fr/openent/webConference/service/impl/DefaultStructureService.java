package fr.openent.webConference.service.impl;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.service.StructureService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultStructureService implements StructureService {
    private static final Logger log = LoggerFactory.getLogger(DefaultStructureService.class);

    @Override
    public void retrieveUAI(String id, Handler<Either<String, JsonObject>> handler) {
        String query = "MATCH (s:Structure {id:{id}}) RETURN s.UAI as uai";
        JsonObject params = new JsonObject().put("id", id);
        Neo4j.getInstance().execute(query, params, Neo4jResult.validUniqueResultHandler(handler));
    }

    private Map<String, String> transformActiveUsersToMap(JsonArray activeUsers) {
        Map<String, String> map = new HashMap<>();
        ((List<JsonObject>) activeUsers.getList()).forEach(user -> map.put(user.getString("owner", ""), user.getString("name", "")));

        return map;
    }

    @Override
    public void retrieveActiveUsers(String id, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT owner, name FROM " + WebConference.ROOM_TABLE + " WHERE structure = ? AND active_session IS NOT NULL;";
        JsonArray params = new JsonArray().add(id);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@DefaultStructureService] Failed to retrieve active users for structure " + id, evt.left().getValue());
                handler.handle(new Either.Left<>(evt.left().getValue()));
            } else {
                JsonArray res = evt.right().getValue();
                Map<String, String> userRoomNameMap = transformActiveUsersToMap(res);
                JsonArray userIds = new JsonArray(((List<JsonObject>) res.getList()).stream().map(o -> o.getString("owner")).collect(Collectors.toList()));
                String neoQuery = "MATCH (u:User) WHERE u.id IN {ids} RETURN u.displayName as displayName, u.id as id";
                Neo4j.getInstance().execute(neoQuery, new JsonObject().put("ids", userIds), Neo4jResult.validResultHandler(neo -> {
                    if (neo.isLeft()) {
                        log.error("[WebConference@DefaultStructureHandler] Failed to retrieve users for identifiers " + userIds.encode(), neo.left().getValue());
                        handler.handle(neo.left());
                        return;
                    }

                    JsonArray users = neo.right().getValue();
                    ((List<JsonObject>) users.getList()).forEach(user -> user.put("roomName", userRoomNameMap.get(user.getString("id"))));
                    handler.handle(new Either.Right<>(users));
                }));
            }
        }));
    }
    
    @Override
    public void getPlatformUAIs(Handler<Either<String, JsonArray>> handler) {
        String query = "MATCH (s:Structure) RETURN s.UAI AS uai";
        Neo4j.getInstance().execute(query, new JsonObject(), Neo4jResult.validResultHandler(evt -> {
            if (evt.isLeft()) {
                handler.handle(evt.left());
            } else {
                List<String> uais = ((List<JsonObject>) evt.right().getValue().getList()).stream().map(structure -> structure.getString("uai")).collect(Collectors.toList());
                uais.removeAll(Collections.singleton(null));
                handler.handle(new Either.Right<>(new JsonArray(uais)));
            }
        }));
    }
}
