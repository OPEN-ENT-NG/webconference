package fr.openent.webConference.service.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;



public class UserService {

    private EventBus eb;

    private static final String DIRECTORY_ADDRESS = "directory";

    public UserService(EventBus eb){
        this.eb = eb;
    }

    public void getUsers(final JsonArray userIds, final JsonArray groupIds,
                         final Handler<Either<String, JsonArray>> handler) {

        JsonObject action = new JsonObject()
                .put("action", "list-users")
                .put("userIds", userIds)
                .put("groupIds", groupIds);
        eb.request(DIRECTORY_ADDRESS, action, handlerToAsyncHandler(validResultHandler(handler)));
    }
}


