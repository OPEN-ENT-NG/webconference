package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.core.Config;
import fr.openent.webConference.event.Event;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;

import static fr.openent.webConference.WebConference.webconfConfig;

public class WebConferenceController extends ControllerHelper {
    private EventStore eventStore;

    public WebConferenceController(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Get("")
    @SecuredAction(WebConference.VIEW_WORKFLOW)
    @ApiDoc("Render default view")
    public void view(HttpServerRequest request) {
        renderView(request);
        eventStore.createAndStoreEvent(Event.ACCESS.name(), request);
    }

    @Get("/allowsPublic")
    @ApiDoc("Check if module allows public links")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void get(HttpServerRequest request) {
        JsonObject allow_public_link = new JsonObject().put("allow_public_link", webconfConfig.getValue("allow-public-link", false));
        Renders.renderJson(request, allow_public_link, 200);
    }

    @Get("/config")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getConfig(final HttpServerRequest request) {
        JsonObject safeConfig = config.copy();

        JsonObject bigbluebuttonConfig = safeConfig.getJsonObject("bigbluebutton", null);
        if (bigbluebuttonConfig != null) {
            if (bigbluebuttonConfig.getString("secret", null) != null) bigbluebuttonConfig.put("secret", "**********");
        }

        renderJson(request, safeConfig);
    }

    @Get("/config/share")
    @ApiDoc("Get the sharing configuration (for example: default actions to check in share panel.")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getConfigShare(final HttpServerRequest request) {
        JsonObject shareConfig = webconfConfig.getJsonObject(Config.SHARE);
        if (shareConfig != null) {
            renderJson(request, shareConfig, 200);
        } else {
            notFound(request, "No platform sharing configuration found");
        }
    }
}
