package fr.openent.webconference.tests;

import fr.openent.webConference.service.GuestLinkService;
import fr.openent.webConference.service.GuestLinkState;
import fr.openent.webConference.service.impl.DefaultGuestLinkService;
import fr.openent.webConference.tiers.MeetingStatus;
import fr.openent.webConference.tiers.RoomProvider;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

// Only the branches that need no database: the ones that renew a link are covered by the acceptance tests.
@RunWith(VertxUnitRunner.class)
public class DefaultGuestLinkServiceTest {
    private static final String SESSION = "session-1";

    private final GuestLinkService service = new DefaultGuestLinkService();
    private RoomProvider provider;

    private static JsonObject room(boolean guestLinkEnabled, String activeSession, String guestSession) {
        return new JsonObject()
                .put("id", "room-1")
                .put("guest_link_enabled", guestLinkEnabled)
                .put("active_session", activeSession)
                .put("guest_session", guestSession);
    }

    @Before
    public void setUp() {
        provider = Mockito.mock(RoomProvider.class);
    }

    @Test
    public void unboundLinkWaitsForTheNextSession(TestContext ctx) {
        service.resolveState(room(true, null, null), provider).onComplete(ctx.asyncAssertSuccess(state -> {
            ctx.assertEquals(GuestLinkState.WAITING, state);
            Mockito.verifyZeroInteractions(provider);
        }));
    }

    @Test
    public void linkBoundToARunningSessionIsOpen(TestContext ctx) {
        Mockito.when(provider.getMeetingStatus(SESSION)).thenReturn(Future.succeededFuture(MeetingStatus.RUNNING));

        service.resolveState(room(true, SESSION, SESSION), provider).onComplete(ctx.asyncAssertSuccess(state ->
                ctx.assertEquals(GuestLinkState.OPEN, state)));
    }

    @Test
    public void linkBoundToASessionNobodyJoinedYetWaits(TestContext ctx) {
        Mockito.when(provider.getMeetingStatus(SESSION)).thenReturn(Future.succeededFuture(MeetingStatus.NOT_RUNNING));

        service.resolveState(room(true, SESSION, SESSION), provider).onComplete(ctx.asyncAssertSuccess(state ->
                ctx.assertEquals(GuestLinkState.WAITING, state)));
    }

    @Test
    public void unreachableProviderFails(TestContext ctx) {
        Mockito.when(provider.getMeetingStatus(SESSION)).thenReturn(Future.failedFuture("BBB unreachable"));

        service.resolveState(room(true, SESSION, SESSION), provider).onComplete(ctx.asyncAssertFailure());
    }

    @Test
    public void disabledLinkIsNeverBound(TestContext ctx) {
        service.bindToRunningSession(room(false, SESSION, null), provider).onComplete(ctx.asyncAssertSuccess(bound -> {
            ctx.assertFalse(bound);
            Mockito.verifyZeroInteractions(provider);
        }));
    }

    @Test
    public void alreadyBoundLinkIsNotBoundAgain(TestContext ctx) {
        service.bindToRunningSession(room(true, SESSION, SESSION), provider).onComplete(ctx.asyncAssertSuccess(bound -> {
            ctx.assertFalse(bound);
            Mockito.verifyZeroInteractions(provider);
        }));
    }

    @Test
    public void linkIsNotBoundWithoutActiveSession(TestContext ctx) {
        service.bindToRunningSession(room(true, null, null), provider).onComplete(ctx.asyncAssertSuccess(bound ->
                ctx.assertFalse(bound)));
    }

    @Test
    public void linkIsNotBoundToASessionThatDoesNotRun(TestContext ctx) {
        Mockito.when(provider.getMeetingStatus(SESSION)).thenReturn(Future.succeededFuture(MeetingStatus.NOT_RUNNING));

        service.bindToRunningSession(room(true, SESSION, null), provider).onComplete(ctx.asyncAssertSuccess(bound ->
                ctx.assertFalse(bound)));
    }
}
