package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
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
        JsonObject allow_public_link = new JsonObject().put("allow_public_link", WebConference.webconfConfig.getValue("allow-public-link", false));
        Renders.renderJson(request, allow_public_link, 200);
    }
}
