package fr.openent.webConference;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;

public class WebConferenceController extends ControllerHelper {

    @Get("")
    @SecuredAction(WebConference.view)
    @ApiDoc("Render default view")
    public void view(HttpServerRequest request) {
        renderView(request);
    }
}
