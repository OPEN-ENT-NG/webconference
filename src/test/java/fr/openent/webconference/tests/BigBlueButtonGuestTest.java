package fr.openent.webconference.tests;

import fr.openent.webConference.bigbluebutton.BigBlueButton;
import fr.openent.webConference.tiers.MeetingStatus;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class BigBlueButtonGuestTest {
    private static final String API_ENDPOINT = "/bigbluebutton/api";
    private static final String SECRET = "test-secret";

    private static final Map<String, String> MEETING_INFO_RESPONSES = new HashMap<>();

    static {
        MEETING_INFO_RESPONSES.put("running", meetingInfo("SUCCESS", "", "true", "0"));
        MEETING_INFO_RESPONSES.put("created", meetingInfo("SUCCESS", "", "false", "0"));
        MEETING_INFO_RESPONSES.put("ended", meetingInfo("SUCCESS", "", "false", "1795117170532"));
        MEETING_INFO_RESPONSES.put("unknown", meetingInfo("FAILED", "notFound", "", ""));
        MEETING_INFO_RESPONSES.put("refused", meetingInfo("FAILED", "checksumError", "", ""));
        MEETING_INFO_RESPONSES.put("garbage", "this is not xml");
    }

    private Vertx vertx;
    private BigBlueButton bbb;

    private static String meetingInfo(String returnCode, String messageKey, String running, String endTime) {
        return "<response><returncode>" + returnCode + "</returncode><messageKey>" + messageKey + "</messageKey>" +
                "<running>" + running + "</running><endTime>" + endTime + "</endTime></response>";
    }

    @Before
    public void setUp(TestContext ctx) {
        vertx = Vertx.vertx();
        vertx.createHttpServer()
                .requestHandler(request -> request.response()
                        .putHeader("Content-Type", "text/xml")
                        .end(MEETING_INFO_RESPONSES.getOrDefault(request.getParam("meetingID"), "")))
                .listen(0)
                .onComplete(ctx.asyncAssertSuccess(server -> {
                    bbb = new BigBlueButton();
                    bbb.setHost(vertx, "http://localhost:" + server.actualPort());
                    bbb.setApiEndpoint(API_ENDPOINT);
                    bbb.setSecret(SECRET);
                    bbb.setSource("test");
                }));
    }

    @After
    public void tearDown(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    @Test
    public void guestRedirectUrlIsSignedWithTheGuestFlag(TestContext ctx) throws Exception {
        String url = bbb.getGuestRedirectURL("session-1", "Jean Dupont (invité)", "attendee-pw");

        String query = url.substring(url.indexOf('?') + 1);
        String parameters = query.substring(0, query.lastIndexOf("&checksum="));
        String checksum = query.substring(query.lastIndexOf("&checksum=") + "&checksum=".length());

        ctx.assertTrue(url.contains(API_ENDPOINT + "/join?"));
        ctx.assertEquals("fullName=" + URLEncoder.encode("Jean Dupont (invité)", "UTF-8") +
                "&meetingID=session-1&password=attendee-pw&guest=true", parameters);
        ctx.assertEquals(sha1("join" + parameters + SECRET), checksum);
    }

    @Test
    public void userRedirectUrlHasNoGuestFlag(TestContext ctx) {
        String url = bbb.getRedirectURL("session-1", "Marie Prof", "moderator-pw");

        ctx.assertFalse(url.contains("guest=true"));
        ctx.assertTrue(url.contains("&meetingID=session-1&password=moderator-pw&checksum="));
    }

    @Test
    public void runningMeetingIsRunning(TestContext ctx) {
        bbb.getMeetingStatus("running").onComplete(ctx.asyncAssertSuccess(status ->
                ctx.assertEquals(MeetingStatus.RUNNING, status)));
    }

    @Test
    public void createdButEmptyMeetingIsNotRunning(TestContext ctx) {
        bbb.getMeetingStatus("created").onComplete(ctx.asyncAssertSuccess(status ->
                ctx.assertEquals(MeetingStatus.NOT_RUNNING, status)));
    }

    @Test
    public void meetingWithAnEndTimeIsEnded(TestContext ctx) {
        bbb.getMeetingStatus("ended").onComplete(ctx.asyncAssertSuccess(status ->
                ctx.assertEquals(MeetingStatus.ENDED, status)));
    }

    @Test
    public void unknownMeetingIsEnded(TestContext ctx) {
        bbb.getMeetingStatus("unknown").onComplete(ctx.asyncAssertSuccess(status ->
                ctx.assertEquals(MeetingStatus.ENDED, status)));
    }

    @Test
    public void refusedRequestFails(TestContext ctx) {
        bbb.getMeetingStatus("refused").onComplete(ctx.asyncAssertFailure());
    }

    @Test
    public void unreadableResponseFails(TestContext ctx) {
        bbb.getMeetingStatus("garbage").onComplete(ctx.asyncAssertFailure());
    }

    private static String sha1(String value) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
