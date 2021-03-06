package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.bigbluebutton.ErrorCode;
import fr.openent.webConference.event.Event;
import fr.openent.webConference.security.RoomFilter;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.SessionService;
import fr.openent.webConference.service.StructureService;
import fr.openent.webConference.service.impl.DefaultRoomService;
import fr.openent.webConference.service.impl.DefaultSessionService;
import fr.openent.webConference.service.impl.DefaultStructureService;
import fr.openent.webConference.tiers.RoomProvider;
import fr.openent.webConference.tiers.RoomProviderPool;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class RoomController extends ControllerHelper {
    static final String RESOURCE_NAME = "room";
    private EventBus eb;
    private RoomService roomService;
    private SessionService sessionService = new DefaultSessionService();
    private StructureService structureService = new DefaultStructureService();
    private EventStore eventStore;
    private final EventHelper eventHelper;

    public RoomController(EventBus eb, JsonObject config, EventStore eventStore) {
        this.eb = eb;
        this.eventStore = eventStore;
        roomService = new DefaultRoomService();
        this.eventHelper = new EventHelper(eventStore);
    }

    @Get("/rooms")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void list(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> roomService.list(user, event -> {
            if (event.isLeft()) {
                renderError(request);
                return;
            }

            RoomProviderPool.getSingleton().getInstance(request, user).setHandler(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@RoomController] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                AtomicInteger counter = new AtomicInteger();
                JsonArray rooms = event.right().getValue();
                for (int i = 0; i < rooms.size(); i++) {
                    if (rooms.getJsonObject(i).containsKey("active_session") && rooms.getJsonObject(i).getString("active_session") != null) {
                        counter.getAndIncrement();
                        int finalI = i;
                        instance.isMeetingRunning(rooms.getJsonObject(i).getString("active_session"), evt -> {
                            if (evt.isLeft()) {
                                log.error("[WebConference@RoomController] Failed to check meeting running for each room.");
                                renderError(request);
                            } else if (!evt.right().getValue()) {
                                rooms.getJsonObject(finalI).remove("active_session");
                            }
                            counter.getAndDecrement();
                            if (counter.get() <= 0) {
                                renderJson(request, rooms);
                            }
                        });
                    }
                }

                if (counter.get() <= 0) {
                    renderJson(request, rooms);
                }
            });
        }));
    }

    @Post("/rooms")
    @SecuredAction(WebConference.create)
    @ApiDoc("Create a room")
    public void create(HttpServerRequest request) {
        String referer = request.headers().contains("referer") ? request.getHeader("referer") : request.scheme() + "://" + getHost(request) + "/webconference";
        final Handler<Either<String, JsonObject>> handler = eventHelper.onCreateResource(request, RESOURCE_NAME, defaultResponseHandler(request));
        RequestUtils.bodyToJson(request, pathPrefix + "room", room -> UserUtils.getUserInfos(eb, request, user -> roomService.create(referer, room, user, handler)));
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

    private void joinAsModerator(JsonObject room, UserInfos user, String locale, final RoomProvider instance, Handler<Either<String, String>> handler) {
        if (room.getString("uai") == null && room.getString("structure") == null) {
            roomService.setStructure(room.getString("id"), user.getStructures().isEmpty() ? "" : user.getStructures().get(0), evt -> {
                if (evt.isLeft()) {
                    log.error("[WebConference@RoomController] Failed to set structure session", evt.left().getValue());
                    handler.handle(new Either.Left<>(evt.left().getValue()));
                } else {
                    joinAsModerator(evt.right().getValue(), user, locale, instance, handler);
                }
            });
            return;
        }

        if (room.containsKey("active_session") && room.getString("active_session") != null) {
        	instance.isMeetingRunning(room.getString("active_session"), evt -> {
                if (evt.isLeft()) handler.handle(new Either.Left<>(evt.left().getValue()));
                else {
                    Boolean isRunning = evt.right().getValue();
                    if (isRunning)
                        handler.handle(new Either.Right<>(instance.getRedirectURL(room.getString("active_session"), user.getUsername(), room.getString("moderator_pw"))));
                    else {
                        room.remove("active_session");
                        joinAsModerator(room, user, locale, instance, handler);
                    }
                }
            });
        } else {
            String sessionId = UUID.randomUUID().toString();
            instance.create(room.getString("name"), sessionId, room.getString("moderator_pw"), room.getString("attendee_pw"), room.getString("uai", ""), locale, creationEvent -> {
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

                    handler.handle(new Either.Right<>(instance.getRedirectURL(sessionId, user.getUsername(), room.getString("moderator_pw"))));
                    eventStore.createAndStoreEvent(Event.ROOM_CREATION.name(), user);
                });
            });
        }
    }

    private void joinAsAttendee(JsonObject room, UserInfos user, String locale, final RoomProvider instance, Handler<Either<String, String>> handler) {
        String activeSessionId = room.getString("active_session");
        if (activeSessionId != null) {
        	instance.isMeetingRunning(activeSessionId, evt -> {
                if (evt.isLeft()) handler.handle(new Either.Left<>(evt.left().getValue()));
                else {
                    Boolean isRunning = evt.right().getValue();
                    if (isRunning) {
                        String url = instance.getRedirectURL(activeSessionId, user.getUsername(), room.getString("attendee_pw"));
                        instance.join(url, canJoinEvent -> {
                            if (canJoinEvent.isLeft()) {
                                log.error("[WebConference@RoomController] Meeting joining checking failed.");
                                handler.handle(new Either.Left<>(canJoinEvent.left().getValue()));
                                return;
                            }

                            handler.handle(new Either.Right<>(url));
                        });
                    }
                    else {
                        room.remove("active_session");
                        joinAsAttendee(room, user, locale, instance, handler);
                    }
                }
            });
        } else {
            handler.handle(new Either.Right<>(""));
        }
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
            RoomProviderPool.getSingleton().getInstance(request, user).setHandler( ar -> {
                RoomProvider instance = ar.result();
                if( instance==null ) {
                	log.error("[WebConference@RoomController] Failed to get a video provider instance.");
                	renderError(request);
                    return;
                }

                JsonObject room = event.right().getValue();
                Handler<Either<String, String>> joiningHandler = evt -> {
                    if (evt.isLeft()) {
                        log.error("[WebConference@BigBlueButton] Failed to join room", evt.left().getValue());
                        ErrorCode code = ErrorCode.get(evt.left().getValue());
                        switch (code) {
                            case TOO_MANY_SESSIONS_PER_STRUCTURE:
                                tooManySessionsPerStructure(request, room);
                                break;
                            case TOO_MANY_USERS:
                                renderView(request, null, ErrorCode.TOO_MANY_USERS.code() + ".html", null);
                                break;
                            case TOO_MANY_SESSIONS:
                                renderView(request, null, ErrorCode.TOO_MANY_SESSIONS.code() + ".html", null);
                                break;
                            default:
                                renderError(request);
                        }
                    } else {
                        String redirect = evt.right().getValue();
                        if (!"".equals(redirect)) {
                            request.response().setStatusCode(302);
                            request.response().putHeader("Location", evt.right().getValue());
                            request.response().putHeader("Client-Server", instance.getSource());
                            request.response().end();
                            eventStore.createAndStoreEvent(Event.ROOM_ACCESS.name(), user);
                        } else {
                            renderView(request, null, "waiting.html", null);
                        }
                    }
                };

                if (user.getUserId().equals(room.getString("owner")))
                    joinAsModerator(room, user, I18n.acceptLanguage(request), instance, joiningHandler);
                else joinAsAttendee(room, user, I18n.acceptLanguage(request), instance, joiningHandler);
            });
        }));
    }

    private void tooManySessionsPerStructure(HttpServerRequest request, JsonObject room) {
        structureService.retrieveActiveUsers(room.getString("structure", ""), evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@RoomController] failed to retrieve structure active users for structure " + room.getString("structure", "<no structure identifier>"), evt.left().getValue());
                renderError(request);
            } else {
                JsonArray users = evt.right().getValue();
                renderView(request, new JsonObject().put("users", users), ErrorCode.TOO_MANY_SESSIONS_PER_STRUCTURE.code() + ".html", null);
            }
        });
    }

    @Get("/rooms/:id/end")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(RoomFilter.class)
    @ApiDoc("Close meeting room")
    public void end(HttpServerRequest request) {
        String roomId = request.getParam("id");
        roomService.get(roomId, evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@RoomController] Failed to retrieve room. Id: " + roomId);
                renderError(request);
                return;
            }

            JsonObject room = evt.right().getValue();
            if (!room.containsKey("active_session")) {
                notFound(request);
                return;
            }

            String activeSession = room.getString("active_session");
            String moderatorPW = room.getString("moderator_pw");

            if (activeSession == null) {
                noContent(request);
                return;
            }

            RoomProviderPool.getSingleton().getInstance(request).setHandler(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@RoomController] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                instance.end(activeSession, moderatorPW, endEvt -> {
                    if (endEvt.isLeft()) {
	                    log.error("[WebConference@RoomController] Failed to end session " + activeSession);
	                    renderError(request);
	                } else {
	                    sessionService.end(activeSession, event -> {
	                        if (event.isLeft()) {
	                            log.error("[WebConference@RoomController] Failed to end sql session " + activeSession, event.left().getValue());
	                            renderError(request);
	                        } else {
	                            noContent(request);
	                        }
	                    });
	                }
	            });
           	});
        });
    }
}
