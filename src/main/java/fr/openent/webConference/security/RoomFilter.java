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

import java.util.ArrayList;
import java.util.List;

public class RoomFilter implements ResourcesProvider {
    @Override
    public void authorize(HttpServerRequest request, Binding binding, UserInfos user, Handler<Boolean> handler) {
        request.pause();
        String id = request.getParam("id");
        String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");

        List<String> gu = new ArrayList();
        gu.add(user.getUserId());
        if (user.getGroupsIds() != null) {
            gu.addAll(user.getGroupsIds());
        }

        String query = "SELECT count(r.id) FROM " + WebConference.ROOM_TABLE + " r " +
                "LEFT JOIN " + WebConference.ROOM_SHARES_TABLE + " rs ON rs.resource_id = r.id " +
                "WHERE ((rs.member_id IN " + Sql.listPrepared(gu) + " AND rs.action = ?) OR r.owner = ?) AND r.id = ?;";
        JsonArray values = new JsonArray(gu).add(sharedMethod).add(user.getUserId()).add(Sql.parseId(id));

        Sql.getInstance().prepared(query, values, event -> {
            request.resume();
            Long count = SqlResult.countResult(event);
            handler.handle(Workflow.hasRight(user, WebConference.CREATE_WORKFLOW) && count != null && count > 0);
        });
    }
}
