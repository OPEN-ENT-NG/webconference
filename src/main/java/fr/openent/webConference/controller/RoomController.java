package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.bigbluebutton.ErrorCode;
import fr.openent.webConference.core.constants.Field;
import fr.openent.webConference.event.Event;
import fr.openent.webConference.security.RoomFilter;
import fr.openent.webConference.service.GuestLinkService;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.SessionService;
import fr.openent.webConference.service.StructureService;
import fr.openent.webConference.service.impl.DefaultGuestLinkService;
import fr.openent.webConference.service.impl.DefaultRoomService;
import fr.openent.webConference.service.impl.DefaultSessionService;
import fr.openent.webConference.service.impl.DefaultStructureService;
import fr.openent.webConference.service.impl.UserService;
import fr.openent.webConference.tiers.RoomProvider;
import fr.openent.webConference.tiers.RoomProviderPool;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class RoomController extends ControllerHelper {
    static final String RESOURCE_NAME = "room";
    private static final String ROOM_MODULE = "WebConference-Room";
    private EventBus eb;
    private RoomService roomService;
    private SessionService sessionService = new DefaultSessionService();
    private final GuestLinkService guestLinkService = new DefaultGuestLinkService();
    private StructureService structureService = new DefaultStructureService();
    private UserService userService;
    private EventStore eventStore;
    private final EventHelper eventHelper;

    public RoomController(EventBus eb, EventStore eventStore) {
        this.eb = eb;
        this.eventStore = eventStore;
        this.roomService = new DefaultRoomService();
        this.eventHelper = new EventHelper(eventStore);
        this.userService = new UserService(eb);
    }

    @SecuredAction(value = WebConference.CONTRIB_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void initContribSharingRight(final HttpServerRequest request) {
    }

    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void initManagerSharingRight(final HttpServerRequest request) {
    }

    @Get("/rooms")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void list(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                log.error("User not found in session.");
                Renders.unauthorized(request);
                return;
            }

            final List<String> groupsAndUserIds = new ArrayList<>();
            groupsAndUserIds.add(user.getUserId());
            if (user.getGroupsIds() != null) {
                groupsAndUserIds.addAll(user.getGroupsIds());
            }

            roomService.list(groupsAndUserIds, user, event -> {
                if (event.isLeft()) {
                    renderError(request);
                    return;
                }

                JsonArray rooms = event.right().getValue();
                RoomProviderPool.getSingleton().getInstance(request, user)
                        .compose(instance -> refreshActiveSessions(rooms, instance))
                        .compose(v -> addGuestLinks(request, rooms, user, groupsAndUserIds))
                        .onSuccess(v -> renderJson(request, rooms))
                        .onFailure(err -> {
                            log.error("[WebConference@list] Failed to list rooms", err);
                            renderError(request);
                        });
            });
        });
    }

    private Future<Void> refreshActiveSessions(JsonArray rooms, RoomProvider instance) {
        List<Future<Void>> checks = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            JsonObject room = rooms.getJsonObject(i);
            String activeSession = room.getString(Field.ACTIVE_SESSION);
            if (activeSession != null) {
                checks.add(refreshActiveSession(room, activeSession, instance));
            }
        }
        return Future.all(checks).mapEmpty();
    }

    private Future<Void> refreshActiveSession(JsonObject room, String activeSession, RoomProvider instance) {
        Promise<Boolean> running = Promise.promise();
        instance.isMeetingRunning(activeSession, evt -> {
            if (evt.isLeft()) {
                running.fail(evt.left().getValue());
            } else {
                running.complete(evt.right().getValue());
            }
        });

        return running.future().compose(isRunning -> {
            if (isRunning) {
                return Future.succeededFuture();
            }
            room.remove(Field.ACTIVE_SESSION);
            if (!room.getBoolean(Field.GUEST_LINK_ENABLED, false)) {
                return Future.succeededFuture();
            }
            return guestLinkService.renewIfEnded(room.getString("id"), activeSession, instance)
                    .recover(err -> {
                        log.error("[WebConference@list] Failed to renew guest link of room " + room.getString("id"), err);
                        return Future.succeededFuture();
                    });
        });
    }

    private Future<Void> addGuestLinks(HttpServerRequest request, JsonArray rooms, UserInfos user, List<String> groupsAndUserIds) {
        if (!WebConference.isGuestLinkAllowed()) {
            return Future.succeededFuture();
        }

        return managedRoomIds(groupsAndUserIds).compose(managedIds -> {
            List<String> roomIds = new ArrayList<>();
            for (int i = 0; i < rooms.size(); i++) {
                JsonObject room = rooms.getJsonObject(i);
                boolean canManage = user.getUserId().equals(room.getString("owner_id")) || managedIds.contains(room.getString("id"));
                if (canManage && room.getBoolean(Field.GUEST_LINK_ENABLED, false)) {
                    roomIds.add(room.getString("id"));
                }
            }
            return guestLinkService.findLinks(roomIds);
        }).compose(links -> {
            for (int i = 0; i < rooms.size(); i++) {
                JsonObject room = rooms.getJsonObject(i);
                JsonObject link = links.get(room.getString("id"));
                if (link != null) {
                    putGuestLink(request, room, link.getString(Field.GUEST_TOKEN), link.getString(Field.GUEST_SESSION));
                }
            }
            return Future.succeededFuture();
        });
    }

    private Future<Set<String>> managedRoomIds(List<String> groupsAndUserIds) {
        Promise<Set<String>> promise = Promise.promise();
        roomService.getAllMyRoomRights(groupsAndUserIds, rights -> {
            if (rights.isLeft()) {
                promise.fail(rights.left().getValue());
                return;
            }
            Set<String> roomIds = new HashSet<>();
            for (int i = 0; i < rights.right().getValue().size(); i++) {
                JsonObject right = rights.right().getValue().getJsonObject(i);
                if (WebConference.MANAGER_SHARING_BEHAVIOUR.equals(right.getString("action"))) {
                    roomIds.add(right.getString("resource_id"));
                }
            }
            promise.complete(roomIds);
        });
        return promise.future();
    }

    private static void putGuestLink(HttpServerRequest request, JsonObject room, String token, String boundSession) {
        room.put("guest_link", getScheme(request) + "://" + getHost(request) + "/webconference/pub/" + token)
                .put("guest_link_state", boundSession == null ? "next" : "current");
    }

    // The raw token never leaves the server: managers get the full link, the others nothing.
    private static JsonObject exposeGuestLink(HttpServerRequest request, JsonObject room) {
        String token = (String) room.remove(Field.GUEST_TOKEN);
        String boundSession = (String) room.remove(Field.GUEST_SESSION);
        if (token != null && WebConference.isGuestLinkAllowed()) {
            putGuestLink(request, room, token, boundSession);
        }
        return room;
    }

    @Get("/rooms/:id")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.CONTRIB_SHARING_RIGHT, type = ActionType.RESOURCE)
    @ApiDoc("Get given room")
    public void get(HttpServerRequest request) {
        String id = request.getParam("id");
        roomService.get(id, result -> {
            if (result.isRight()) {
                result.right().getValue().remove(Field.GUEST_TOKEN);
                result.right().getValue().remove(Field.GUEST_SESSION);
            }
            defaultResponseHandler(request).handle(result);
        });
    }

    @Post("/rooms/:id/guest-link")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
    @ApiDoc("Replace the guest link of a room: the previous link stops working at once")
    public void regenerateGuestLink(HttpServerRequest request) {
        String id = request.getParam("id");
        if (!WebConference.isGuestLinkAllowed()) {
            forbidden(request);
            return;
        }

        guestLinkService.regenerate(id).onComplete(ar -> {
            if (ar.failed()) {
                log.error("[WebConference@regenerateGuestLink] Failed to regenerate guest link of room " + id, ar.cause());
                renderError(request);
                return;
            }
            if (ar.result() == null) {
                notFound(request);
                return;
            }
            renderJson(request, exposeGuestLink(request, ar.result()));
        });
    }

    @Post("/rooms/:isPublic")
    @SecuredAction(WebConference.CREATE_WORKFLOW)
    @ApiDoc("Create a room")
    public void create(HttpServerRequest request) {
        boolean guestLinkEnabled = WebConference.isGuestLinkAllowed() && Boolean.parseBoolean(request.getParam("isPublic"));
        String referer = request.headers().contains("referer") ? request.getHeader("referer") : request.scheme() + "://" + getHost(request) + "/webconference";
        final Handler<Either<String, JsonObject>> handler = eventHelper.onCreateResource(request, RESOURCE_NAME, defaultResponseHandler(request));
        RequestUtils.bodyToJson(request, pathPrefix + "room", room -> {
            UserUtils.getUserInfos(eb, request, user -> {
                if (room.getString(Field.NAME, "").isEmpty()) {
                    log.error("[WebConference@RoomController::create] Room name is empty");
                    handler.handle(new Either.Left<>("[WebConference@RoomController::create] Room name is empty"));
                    return;
                }
                roomService.create(referer, room, user, created -> {
                    if (created.isLeft() || !guestLinkEnabled) {
                        handler.handle(created);
                        return;
                    }
                    guestLinkService.setEnabled(created.right().getValue().getString("id"), true)
                            .onSuccess(saved -> handler.handle(new Either.Right<>(exposeGuestLink(request, saved))))
                            .onFailure(err -> handler.handle(new Either.Left<>(err.getMessage())));
                });
            });
        });
    }

    @Put("/rooms/:id/:isPublic")
    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
    @ResourceFilter(RoomFilter.class)
    @ApiDoc("Upate given room")
    public void update(HttpServerRequest request) {
        String id = request.getParam("id");
        boolean guestLinkEnabled = WebConference.isGuestLinkAllowed() && Boolean.parseBoolean(request.getParam("isPublic"));
        Handler<Either<String, JsonObject>> handler = defaultResponseHandler(request);
        RequestUtils.bodyToJson(request, pathPrefix + "room", room -> roomService.update(id, room, updated -> {
            if (updated.isLeft()) {
                handler.handle(updated);
                return;
            }
            // Enabling the link during a session makes it valid for that session right away.
            guestLinkService.setEnabled(id, guestLinkEnabled)
                    .compose(saved -> RoomProviderPool.getSingleton().getInstance(request)
                            .compose(provider -> guestLinkService.bindToRunningSession(saved, provider))
                            .map(bound -> bound ? saved.put(Field.GUEST_SESSION, saved.getString(Field.ACTIVE_SESSION)) : saved))
                    .onSuccess(saved -> handler.handle(new Either.Right<>(exposeGuestLink(request, saved))))
                    .onFailure(err -> {
                        log.error("[WebConference@RoomController::update] Failed to update guest link of room " + id, err);
                        handler.handle(new Either.Left<>(err.getMessage()));
                    });
        }));
    }

    @Delete("/rooms/:id")
    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
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
                    log.error("[WebConference@joinAsModerator] Failed to set structure session", evt.left().getValue());
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
            String urlStream = room.getBoolean("allow_streaming") ? room.getString("streaming_link") + "/" + room.getString("streaming_key") : null;
            boolean waitingRoom = Boolean.TRUE.equals(room.getBoolean(Field.ALLOW_WAITING_ROOM)) || room.getBoolean(Field.GUEST_LINK_ENABLED, false);
            instance.create(room.getString("name"), sessionId, room.getString("id"), room.getString("moderator_pw"), room.getString("attendee_pw"), room.getString("uai", ""), locale, waitingRoom, urlStream, creationEvent -> {
                if (creationEvent.isLeft()) {
                    log.error("[WebConference@joinAsModerator] Failed to join room. Session creation failed.");
                    handler.handle(new Either.Left<>(creationEvent.left().getValue()));
                    return;
                }

                String internalId = creationEvent.right().getValue();
                sessionService.create(sessionId, room.getString("id"), internalId, createSessionEvent -> {
                    if (createSessionEvent.isLeft()) {
                        log.error("[WebConference@joinAsModerator] Failed to save session.");
                        handler.handle(new Either.Left<>(createSessionEvent.left().toString()));
                        return;
                    }

                    if (room.getBoolean(Field.GUEST_LINK_ENABLED, false)) {
                        guestLinkService.bindToSession(room.getString("id"), sessionId)
                                .onFailure(err -> log.error("[WebConference@joinAsModerator] Failed to bind guest link to session " + sessionId, err));
                    }

                    room.remove("opener");
                    room.put("opener", user.getUsername());
                    roomService.update(room.getString("id"), room, updateRoomEvent -> {
                        if (updateRoomEvent.isLeft()) {
                            log.error("[WebConference@joinAsModerator] Failed to update room opener for room : " + room.getString("id"));
                            handler.handle(new Either.Left<>(updateRoomEvent.left().toString()));
                            return;
                        }

                        handler.handle(new Either.Right<>(instance.getRedirectURL(sessionId, user.getUsername(), room.getString("moderator_pw"))));
                        if (Boolean.TRUE.equals(config.getBoolean("enable-old-events", true))) {
                            eventStore.createAndStoreEvent(Event.ROOM_CREATION.name(), user);
                        }
                        eventStore.createAndStoreEvent(Event.CREATE.name(), user, new JsonObject().put("resource-type", ROOM_MODULE).put("override-module", ROOM_MODULE));
                    });
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
                                log.error("[WebConference@joinAsAttendee] Meeting joining checking failed.");
                                handler.handle(new Either.Left<>(canJoinEvent.left().getValue()));
                                return;
                            }

                            handler.handle(new Either.Right<>(url));
                        });
                    } else {
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
            RoomProviderPool.getSingleton().getInstance(request, user).onComplete(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@join] Failed to get a video provider instance.");
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
                            if (Boolean.TRUE.equals(config.getBoolean("enable-old-events", true))) {
                                eventStore.createAndStoreEvent(Event.ROOM_ACCESS.name(), user);
                            }
                            eventStore.createAndStoreEvent(Event.ACCESS.name(), request, new JsonObject().put("override-module", ROOM_MODULE));
                        } else {
                            renderView(request, null, "waiting.html", null);
                        }
                    }
                };

                String roomId = room.getString("id");
                roomService.getUsersShared(roomId, usersShared -> {
                    if (usersShared.isLeft()) {
                        log.error("[WebConference@join] Failed to get users with shared rights on room " + roomId);
                        renderError(request);
                        return;
                    }

                    JsonArray authorizedUsers = new JsonArray();
                    for (int i = 0; i < usersShared.right().getValue().size(); i++) {
                        authorizedUsers.add(usersShared.right().getValue().getJsonObject(i).getString("member_id"));
                    }

                    userService.getUsers(authorizedUsers, authorizedUsers, userEvent -> {
                        String userId = user.getUserId();
                        JsonArray usersJson = userEvent.right().getValue(); //get all users able to access and modify conference room
                        JsonArray users = new JsonArray();

                        for (int i = 0; i < usersJson.size(); ++i) {
                            JsonObject userEach = usersJson.getJsonObject(i); // seperate each user into a JsonObject
                            String idUser = userEach.getString("id"); // get their id and transform into string
                            users.add(idUser);
                        }

                        if (userId.equals(room.getString("owner")) || users.contains(userId)) {
                            joinAsModerator(room, user, I18n.acceptLanguage(request), instance, joiningHandler);
                        } else {
                            joinAsAttendee(room, user, I18n.acceptLanguage(request), instance, joiningHandler);
                        }
                    });
                });
            });
        }));
    }


    private void tooManySessionsPerStructure(HttpServerRequest request, JsonObject room) {
        structureService.retrieveActiveUsers(room.getString("structure", ""), evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@tooManySessionsPerStructure] Failed to retrieve active users for structure " + room.getString("structure", "<no structure identifier>"), evt.left().getValue());
                renderError(request);
            } else {
                JsonArray users = evt.right().getValue();
                renderView(request, new JsonObject().put("users", users), ErrorCode.TOO_MANY_SESSIONS_PER_STRUCTURE.code() + ".html", null);
            }
        });
    }

    @Get("/rooms/:id/end")
    @SecuredAction(value = WebConference.CONTRIB_SHARING_RIGHT, type = ActionType.RESOURCE)
    @ResourceFilter(RoomFilter.class)
    @ApiDoc("Close meeting room")
    public void end(HttpServerRequest request) {
        String roomId = request.getParam("id");
        roomService.get(roomId, evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@end] Failed to retrieve room : " + roomId);
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

            RoomProviderPool.getSingleton().getInstance(request).onComplete(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@end] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                instance.end(activeSession, moderatorPW, endEvt -> {
                    if (endEvt.isLeft()) {
                        log.error("[WebConference@end] Failed to end session " + activeSession);
                        renderError(request);
                    } else {
                        sessionService.end(activeSession, event -> {
                            if (event.isLeft()) {
                                log.error("[WebConference@end] Failed to end sql session " + activeSession, event.left().getValue());
                                renderError(request);
                            } else {
                                room.remove("opener");
                                roomService.update(room.getString("id"), room, updateRoomEvent -> {
                                    if (updateRoomEvent.isLeft()) {
                                        log.error("[WebConference@end] Failed to update room opener for room : " + room.getString("id"));
                                        renderError(request);
                                    }

                                    noContent(request);
                                });
                            }
                        });
                    }
                });
            });
        });
    }

    @Get("/rooms/:id/running")
    @ApiDoc("Is meeting meeting")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.CONTRIB_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void isMeetingRunning(HttpServerRequest request) {
        String roomId = request.getParam("id");
        roomService.get(roomId, evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@getMeetingInfos] Failed to retrieve room : " + roomId);
                renderError(request);
                return;
            }

            JsonObject room = evt.right().getValue();
            if (!room.containsKey("active_session")) {
                notFound(request);
                return;
            }

            String activeSession = room.getString("active_session");

            if (activeSession == null) {
                noContent(request);
                return;
            }

            RoomProviderPool.getSingleton().getInstance(request).onComplete(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@getMeetingInfos] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                instance.isMeetingRunning(activeSession, isRunning -> {
                    if (isRunning.isLeft()) {
                        log.error("[WebConference@list] Failed to check meeting running meeting : " + activeSession);
                        renderError(request);
                    }
                    renderJson(request, new JsonObject().put("running", isRunning.right().getValue()));
                });
            });
        });
    }

    @Get("/rooms/:id/meetingInfo")
    @ApiDoc("Get infos about a current meeting")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.CONTRIB_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void getMeetingInfo(HttpServerRequest request) {
        String roomId = request.getParam("id");
        roomService.get(roomId, evt -> {
            if (evt.isLeft()) {
                log.error("[WebConference@getMeetingInfos] Failed to retrieve room : " + roomId);
                renderError(request);
                return;
            }

            JsonObject room = evt.right().getValue();
            if (!room.containsKey("active_session")) {
                notFound(request);
                return;
            }

            String activeSession = room.getString("active_session");

            if (activeSession == null) {
                noContent(request);
                return;
            }

            RoomProviderPool.getSingleton().getInstance(request).onComplete(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@getMeetingInfos] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                instance.getMeetingInfo(activeSession, getMeetingInfoEvt -> {
                    if (getMeetingInfoEvt.isLeft()) {
                        log.error("[WebConference@getMeetingInfos] Failed to get meeting infos session " + activeSession);
                        renderError(request);
                    } else {
                        renderJson(request, getMeetingInfoEvt.right().getValue());
                    }
                });
            });
        });
    }

    @Post("/rooms/:id/invitation")
    @ApiDoc("Send an invitation by mail to all the wanted users")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void sendInvitation(HttpServerRequest request) {
        RequestUtils.bodyToJson(request, mail -> {
            UserUtils.getUserInfos(eb, request, user -> {
                if (user != null) {
                    JsonArray invitees = mail.getJsonArray("invitees");
                    JsonArray localRespondersIds = new JsonArray();
                    JsonArray listMails = new JsonArray();

                    // Generate list of mails to send
                    for (int i = 0; i < invitees.size(); i++) {
                        String id = invitees.getString(i);
                        if (!localRespondersIds.contains(id)) {
                            localRespondersIds.add(id);
                        }

                        // Generate new mail object if limit or end loop are reached
                        if (i == invitees.size() - 1 || localRespondersIds.size() == config.getInteger("zimbra-max-recipients", 50)) {
                            JsonObject message = new JsonObject()
                                    .put("subject", mail.getString("subject"))
                                    .put("body", mail.getString("body"))
                                    .put("to", new JsonArray())
                                    .put("cci", localRespondersIds);

                            JsonObject action = new JsonObject()
                                    .put("action", "send")
                                    .put("userId", user.getUserId())
                                    .put("username", user.getUsername())
                                    .put("message", message);

                            listMails.add(action);
                            localRespondersIds = new JsonArray();
                        }
                    }

                    // Prepare futures to get message responses
                    List<Promise<JsonObject>> mails = new ArrayList<>();
                    mails.addAll(Collections.nCopies(listMails.size(), Promise.promise()));

                    // Code to send mails
                    for (int i = 0; i < listMails.size(); i++) {
                        Promise<JsonObject> promise = mails.get(i);

                        // Send mail via Conversation app if it exists or else with Zimbra
                        eb.request("org.entcore.conversation", listMails.getJsonObject(i), (Handler<AsyncResult<Message<JsonObject>>>) messageEvent -> {
                            if (!"ok".equals(messageEvent.result().body().getString("status"))) {
                                log.error("[Formulaire@sendReminder] Failed to send reminder : " + messageEvent.cause());
                                promise.handle(Future.failedFuture(messageEvent.cause()));
                            }
                            promise.handle(Future.succeededFuture(messageEvent.result().body()));
                        });
                    }

                    List<Future<JsonObject>> mailsFuture = mails.stream()
                            .map(Promise::future)
                            .collect(Collectors.toList());

                    // Try to send effectively mails with code below and get results
                    Future.all(mailsFuture).onComplete(evt -> {
                        if (evt.failed()) {
                            log.error("[Zimbra@sendMessage] Failed to send reminder : " + evt.cause());
                            Future.failedFuture(evt.cause());
                        }
                        ok(request);
                    });
                } else {
                    log.error("User not found in session.");
                    Renders.unauthorized(request);
                }
            });
        });
    }


    @Get("/rooms/rights/all")
    @ApiDoc("Get my rights for all the rooms")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getAllMyRoomRights(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                List<String> groupsAndUserIds = new ArrayList();
                groupsAndUserIds.add(user.getUserId());
                if (user.getGroupsIds() != null) {
                    groupsAndUserIds.addAll(user.getGroupsIds());
                }
                roomService.getAllMyRoomRights(groupsAndUserIds, arrayResponseHandler(request));
            } else {
                log.error("User not found in session.");
                Renders.unauthorized(request);
            }
        });
    }

    // Sharing functions

    @Override
    @Get("/share/json/:id")
    @ApiDoc("List rights for a given room")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void shareJson(final HttpServerRequest request) {
        super.shareJson(request, false);
    }

    @Put("/share/json/:id")
    @ApiDoc("Add rights for a given room")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void shareSubmit(final HttpServerRequest request) {
        super.shareJsonSubmit(request, null, false);
    }

    @Put("/share/resource/:id")
    @ApiDoc("Add rights for a given room")
    @ResourceFilter(RoomFilter.class)
    @SecuredAction(value = WebConference.MANAGER_SHARING_RIGHT, type = ActionType.RESOURCE)
    public void shareResource(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "share", share -> {
            UserUtils.getUserInfos(eb, request, user -> {
                if (user != null) {
                    final String roomId = request.params().get("id");
                    Map<String, Object> idUsers = share.getJsonObject("users").getMap();
                    Map<String, Object> idGroups = share.getJsonObject("groups").getMap();
                    Map<String, Object> idBookmarks = share.getJsonObject("bookmarks").getMap();

                    // Update 'collab' property as needed
                    List<Map<String, Object>> idsObjects = new ArrayList<>();
                    idsObjects.add(idUsers);
                    idsObjects.add(idGroups);
                    idsObjects.add(idBookmarks);
                    updateRoomCollabProp(roomId, user, idsObjects);

                    // Fix bug auto-unsharing
                    roomService.getSharedWithMe(roomId, user, event -> {
                        if (event.isRight() && event.right().getValue() != null) {
                            JsonArray rights = event.right().getValue();
                            String id = user.getUserId();
                            share.getJsonObject("users").put(id, new JsonArray());

                            for (int i = 0; i < rights.size(); i++) {
                                JsonObject right = rights.getJsonObject(i);
                                share.getJsonObject("users").getJsonArray(id).add(right.getString("action"));
                            }

                            // Classic sharing stuff (putting or removing ids from form_shares table accordingly)
                            this.getShareService().share(user.getUserId(), roomId, share, r -> {
                                if (r.isRight()) {
                                    this.doShareSucceed(request, roomId, user, share, r.right().getValue(), false);
                                } else {
                                    JsonObject error = (new JsonObject()).put("error", r.left().getValue());
                                    Renders.renderJson(request, error, 400);
                                }
                            });
                        } else {
                            log.error("[WebConference@getSharedWithMe] Fail to get user's shared rights");
                        }
                    });
                } else {
                    log.error("User not found in session.");
                    unauthorized(request);
                }
            });
        });
    }

    private void updateRoomCollabProp(String roomId, UserInfos user, List<Map<String, Object>> idsObjects) {
        roomService.get(roomId, getEvent -> {
            if (getEvent.isRight()) {
                JsonObject room = getEvent.right().getValue();

                boolean isShared = false;
                int i = 0;
                while (!isShared && i < idsObjects.size()) { // Iterate over "users", "groups", "bookmarks"
                    int j = 0;
                    Map<String, Object> o = idsObjects.get(i);
                    List<Object> values = new ArrayList<>(o.values());

                    while (!isShared && j < values.size()) { // Iterate over each pair id-actions
                        if (values.get(j) instanceof List) {
                            List<String> actions = (ArrayList) (values.get(j));

                            int k = 0;
                            while (!isShared && k < actions.size()) { // Iterate over each action for an id
                                if (actions.get(k).equals(WebConference.CONTRIB_SHARING_BEHAVIOUR) ||
                                        actions.get(k).equals(WebConference.MANAGER_SHARING_BEHAVIOUR)) {
                                    isShared = true;
                                }
                                k++;
                            }
                        }

                        j++;
                    }
                    i++;
                }

                if (!isShared && !room.getString("owner").equals(user.getUserId())) {
                    isShared = true;
                }

                room.put("collab", isShared);
                roomService.update(roomId, room, updateEvent -> {
                    if (updateEvent.isLeft()) {
                        log.error("[WebConference@updateRoomCollabProp] Fail to update room : " + updateEvent.left().getValue());
                    }
                });
            } else {
                log.error("[WebConference@updateRoomCollabProp] Fail to get room : " + getEvent.left().getValue());
            }
        });
    }

    //Streaming API

    @Get("/rooms/:id/stream/start")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    @ApiDoc("Start stream by room id")
    public void startStream(HttpServerRequest request) {
        String id = request.getParam("id");
        UserUtils.getUserInfos(eb, request, user -> roomService.get(id, event -> {
            if (event.isLeft()) {
                renderError(request);
                return;
            }
            RoomProviderPool.getSingleton().getInstance(request, user).onComplete(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@join] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                JsonObject room = event.right().getValue();
                String activeSession = room.getString("active_session");

                if (activeSession == null) {
                    noContent(request);
                    return;
                }
                instance.startStreaming(activeSession, startStreamingEvent -> {
                    if (startStreamingEvent.isLeft()) {
                        log.error("[WebConference@list] Failed to check meeting running meeting : " + activeSession);
                        renderError(request);
                    }
                    renderJson(request, new JsonObject());
                });

            });
        }));
    }

    @Get("/rooms/:id/stream/stop")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    @ApiDoc("Stop stream by room id")
    public void stopStream(HttpServerRequest request) {
        String id = request.getParam("id");
        UserUtils.getUserInfos(eb, request, user -> roomService.get(id, event -> {
            if (event.isLeft()) {
                renderError(request);
                return;
            }
            RoomProviderPool.getSingleton().getInstance(request, user).onComplete(ar -> {
                RoomProvider instance = ar.result();
                if (instance == null) {
                    log.error("[WebConference@join] Failed to get a video provider instance.");
                    renderError(request);
                    return;
                }

                JsonObject room = event.right().getValue();
                String activeSession = room.getString("active_session");

                if (activeSession == null) {
                    noContent(request);
                    return;
                }
                instance.stopStreaming(activeSession, stopStreamingEvent -> {
                    if (stopStreamingEvent.isLeft()) {
                        log.error("[WebConference@list] Failed to check meeting running meeting : " + activeSession);
                        renderError(request);
                    }
                    renderJson(request, new JsonObject());
                });

            });
        }));
    }

}
