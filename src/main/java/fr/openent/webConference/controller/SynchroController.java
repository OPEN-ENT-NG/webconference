package fr.openent.webConference.controller;

import fr.openent.webConference.service.StructureService;
import fr.openent.webConference.service.impl.DefaultStructureService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.security.ActionType;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

public class SynchroController extends ControllerHelper {
    private StructureService structureService = new DefaultStructureService();

    public SynchroController() {
        super();
    }

    @Get("/synchro/structures")
    @ApiDoc("Render all structures UAI")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getStructuresUAI(HttpServerRequest request) {
        structureService.getPlatformUAIs(arrayResponseHandler(request));
    }
}