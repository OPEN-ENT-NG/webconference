package fr.openent.webConference.security;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.helper.Workflow;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

public class RoomFilter implements ResourcesProvider {
    @Override
    public void authorize(HttpServerRequest request, Binding binding, UserInfos user, Handler<Boolean> handler) {
        request.pause();
        String id = request.getParam("id");
        String query = "SELECT count(id) FROM " + WebConference.DB_SCHEMA + ".room WHERE id = ? and owner = ?";
        Sql.getInstance().prepared(query, new JsonArray().add(id).add(user.getUserId()), event -> {
            request.resume();
            Long count = SqlResult.countResult(event);
            handler.handle(Workflow.hasRight(user, WebConference.create) && count != null && count > 0);
        });
    }
}
