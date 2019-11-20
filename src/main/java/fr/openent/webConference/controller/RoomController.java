package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.secutiry.RoomFilter;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.impl.DefaultRoomService;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class RoomController extends ControllerHelper {
    private EventBus eb;
    private RoomService roomService;

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
}
