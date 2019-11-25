package fr.openent.webConference.controller;

import fr.openent.webConference.bigbluebutton.Event;
import fr.openent.webConference.service.SessionService;
import fr.openent.webConference.service.impl.DefaultSessionService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.security.XSSUtils;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;

public class WebHookController extends ControllerHelper {

    private SessionService sessionService = new DefaultSessionService();

    @Post("/webhook")
    @ApiDoc("Event WebHook")
    public void webhook(HttpServerRequest request) {
        noContent(request);
        request.bodyHandler(buffer -> {
            Event evt = new Event(XSSUtils.stripXSS(buffer.toString("UTF-8")));
            if (evt.isMeetingEnded()) {
                sessionService.end(evt.getMeetingId(), event -> {
                    if (event.isLeft()) log.error("[WebConference@WebhookController] Failed to end meeting");
                    else log.info("[WebConference@WebhookController] End meeting : " + evt.getMeetingId());
                });
            }
        });
    }

    @Get("/Webhook")
    public void getWebHook(HttpServerRequest request) {
        noContent(request);
    }
}
