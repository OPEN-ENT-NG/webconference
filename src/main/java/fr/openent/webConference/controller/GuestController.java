package fr.openent.webConference.controller;

import fr.openent.webConference.WebConference;
import fr.openent.webConference.core.constants.Field;
import fr.openent.webConference.service.GuestLinkService;
import fr.openent.webConference.service.GuestLinkState;
import fr.openent.webConference.service.impl.DefaultGuestLinkService;
import fr.openent.webConference.tiers.RoomProvider;
import fr.openent.webConference.tiers.RoomProviderPool;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.I18n;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

import java.util.Locale;
import java.util.regex.Pattern;

public class GuestController extends ControllerHelper {
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final int MAX_NAME_LENGTH = 64;
    private static final String GUEST_PAGE_PATH = "/webconference/pub/";
    private static final String NAME_SUFFIX_KEY = "webconference.guest.suffix";
    private static final String ERROR_INVALID_NAME = "invalid-name";
    private static final String ERROR_UNAVAILABLE = "unavailable";

    private final GuestLinkService guestLinkService = new DefaultGuestLinkService();

    @Get("/pub/:token/status")
    @ApiDoc("Get the state of a guest link: waiting, open or expired")
    public void status(HttpServerRequest request) {
        resolveAccess(request).onComplete(ar -> {
            if (ar.failed()) {
                log.error("[WebConference@GuestController::status] Failed to resolve guest link " + shortToken(request), ar.cause());
                renderError(request);
                return;
            }
            setGuestHeaders(request);
            renderJson(request, new JsonObject().put("state", ar.result().state.name().toLowerCase(Locale.ROOT)));
        });
    }

    @Post("/pub/:token/join")
    @ApiDoc("Redirect a guest to the running session of the room")
    public void join(HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.endHandler(v -> {
            String name = sanitizeName(request.formAttributes().get("name"));
            if (name == null) {
                redirectToGuestPage(request, ERROR_INVALID_NAME);
                return;
            }

            resolveAccess(request).onComplete(ar -> {
                if (ar.failed()) {
                    log.error("[WebConference@GuestController::join] Failed to resolve guest link " + shortToken(request), ar.cause());
                    redirectToGuestPage(request, ERROR_UNAVAILABLE);
                    return;
                }

                GuestAccess access = ar.result();
                if (access.state != GuestLinkState.OPEN) {
                    redirectToGuestPage(request, null);
                    return;
                }

                String suffix = I18n.getInstance().translate(NAME_SUFFIX_KEY, getHost(request), I18n.acceptLanguage(request));
                String url = access.provider.getGuestRedirectURL(
                        access.room.getString(Field.GUEST_SESSION),
                        name + " (" + suffix + ")",
                        access.room.getString("attendee_pw"));
                setGuestHeaders(request);
                request.response().setStatusCode(303).putHeader("Location", url).end();
            });
        });
    }

    private Future<GuestAccess> resolveAccess(HttpServerRequest request) {
        String token = request.getParam("token");
        if (!WebConference.isGuestLinkAllowed() || !isValidToken(token)) {
            return Future.succeededFuture(GuestAccess.EXPIRED);
        }

        return guestLinkService.findRoomByToken(token).compose(room -> {
            if (room == null) {
                return Future.succeededFuture(GuestAccess.EXPIRED);
            }
            return RoomProviderPool.getSingleton().getInstanceForRoom(request, room)
                    .compose(provider -> guestLinkService.resolveState(room, provider)
                            .map(state -> new GuestAccess(state, room, provider)));
        });
    }

    private void redirectToGuestPage(HttpServerRequest request, String error) {
        String token = request.getParam("token");
        String location = GUEST_PAGE_PATH + (isValidToken(token) ? token : "invalid") + (error == null ? "" : "?error=" + error);
        setGuestHeaders(request);
        request.response().setStatusCode(303).putHeader("Location", location).end();
    }

    private static void setGuestHeaders(HttpServerRequest request) {
        request.response()
                .putHeader("Cache-Control", "no-store")
                .putHeader("Referrer-Policy", "no-referrer")
                .putHeader("X-Robots-Tag", "noindex");
    }

    private static boolean isValidToken(String token) {
        return token != null && TOKEN_FORMAT.matcher(token).matches();
    }

    private static String sanitizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = CONTROL_CHARS.matcher(rawName).replaceAll("").trim();
        return name.isEmpty() || name.length() > MAX_NAME_LENGTH ? null : name;
    }

    private static String shortToken(HttpServerRequest request) {
        String token = request.getParam("token");
        return token == null ? "" : token.substring(0, Math.min(8, token.length()));
    }

    private static final class GuestAccess {
        private static final GuestAccess EXPIRED = new GuestAccess(GuestLinkState.EXPIRED, null, null);

        private final GuestLinkState state;
        private final JsonObject room;
        private final RoomProvider provider;

        private GuestAccess(GuestLinkState state, JsonObject room, RoomProvider provider) {
            this.state = state;
            this.room = room;
            this.provider = provider;
        }
    }
}
