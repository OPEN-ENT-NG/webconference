package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.event.Event;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;

public class WebConferenceController extends ControllerHelper {
    private EventStore eventStore;

    public WebConferenceController(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Get("")
    @SecuredAction(WebConference.VIEW_WORKFLOW)
    @ApiDoc("Render default view")
    public void view(HttpServerRequest request) {
        JsonObject params = new JsonObject().put("allowPublic", WebConference.publicConf.allowed());
        renderView(request, params);
        eventStore.createAndStoreEvent(Event.ACCESS.name(), request);
    }
}
