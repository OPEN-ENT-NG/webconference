package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.event.Event;
import fr.openent.webConference.security.RoomFilter;
import fr.openent.webConference.service.RoomService;
import fr.openent.webConference.service.impl.DefaultRoomService;
import fr.openent.webConference.tiers.RoomProvider;
import fr.openent.webConference.tiers.RoomProviderPool;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;

public class StreamController extends ControllerHelper {
    private EventStore eventStore;
    private EventBus eb;
    private RoomService roomService;

    public StreamController(EventBus eb, EventStore eventStore) {
        this.eb = eb;
        this.eventStore = eventStore;
        roomService = new DefaultRoomService();
    }

    @Post("/stream/create/:id")
    @SecuredAction(WebConference.STREAMING_WORKFLOW)
    @ResourceFilter(RoomFilter.class)
    @ApiDoc("Create a stream session")
    public void createStreaming(HttpServerRequest request) {
        String id = request.getParam("id");
        UserUtils.getUserInfos(eb, request, user -> {
            roomService.get(id, roomEvent -> {
                JsonObject room = roomEvent.right().getValue();
                if (!room.containsKey("active_session")) {
                    notFound(request);
                    return;
                }

                String activeSession = room.getString("active_session");

                if (activeSession == null) {
                    noContent(request);
                    return;
                }

                RoomProviderPool.getSingleton().getInstance(request).setHandler(ar -> {
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
                eventStore.createAndStoreEvent(Event.STREAM_CREATION.name(), user);
            });
        });
    }
}
