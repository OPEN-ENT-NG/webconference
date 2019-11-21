package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.bigbluebutton.BigBlueButton;
import fr.openent.webConference.secutiry.RoomFilter;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.SessionService;
import fr.openent.webConference.service.impl.DefaultRoomService;
import fr.openent.webConference.service.impl.DefaultSessionService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.UUID;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class RoomController extends ControllerHelper {
    private EventBus eb;
    private RoomService roomService;
    private SessionService sessionService = new DefaultSessionService();

    public RoomController(EventBus eb, JsonObject config) {
        this.eb = eb;
        roomService = new DefaultRoomService(config.getString("host"));
    }

    @Get("/rooms")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void list(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> roomService.list(user, arrayResponseHandler(request)));
    }

    @Post("/rooms")
    @SecuredAction(WebConference.create)
    @ApiDoc("Create a room")
    public void create(HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "room", room -> UserUtils.getUserInfos(eb, request, user -> roomService.create(room, user, defaultResponseHandler(request))));
    }

    @Put("/rooms/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(RoomFilter.class)
    @ApiDoc("Upate given room")
    public void update(HttpServerRequest request) {
        String id = request.getParam("id");
        RequestUtils.bodyToJson(request, pathPrefix + "room", room -> roomService.update(id, room, defaultResponseHandler(request)));
    }

    @Delete("/rooms/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(RoomFilter.class)
    @ApiDoc("Delete given room")
    public void delete(HttpServerRequest request) {
        String id = request.getParam("id");
        roomService.delete(id, defaultResponseHandler(request));
    }

    private void joinAsModerator(JsonObject room, UserInfos user, Handler<Either<String, String>> handler) {
        String sessionId = UUID.randomUUID().toString();
        BigBlueButton.getInstance().create(room.getString("name"), sessionId, room.getString("moderator_pw"), room.getString("attendee_pw"), creationEvent -> {
            if (creationEvent.isLeft()) {
                log.error("[WebConference@RoomController] Failed to join room. Session creation failed.");
                handler.handle(new Either.Left<>(creationEvent.left().getValue()));
                return;
            }

            String internalId = creationEvent.right().getValue();
            sessionService.create(sessionId, room.getString("id"), internalId, event -> {
                if (event.isLeft()) {
                    log.error("[WebConference@RoomController] Failed to save session.");
                    handler.handle(new Either.Left<>(event.left().toString()));
                    return;
                }

                handler.handle(new Either.Right<>(BigBlueButton.getInstance().getRedirectURL(sessionId, user.getUsername(), room.getString("moderator_pw"))));
            });
        });
    }

    @Get("/rooms/:id/join")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    @ApiDoc("Join given room")
    public void join(HttpServerRequest request) {
        String id = request.getParam("id");
        UserUtils.getUserInfos(eb, request, user -> roomService.get(id, event -> {
            if (event.isLeft()) {
                renderError(request);
                return;
            }

            JsonObject room = event.right().getValue();
            Handler<Either<String, String>> joiningHandler = evt -> {
                if (evt.isLeft()) {
                    log.error("[WebConference@BigBlueButton] Failed to join room", evt.left().getValue());
                    renderError(request);
                } else {
                    request.response().setStatusCode(302);
                    request.response().putHeader("Location", evt.right().getValue());
                    request.response().end();
                }
            };

            if (user.getUserId().equals(room.getString("owner"))) {
                joinAsModerator(room, user, joiningHandler);
            }
        }));
    }
}
