package fr.openent.webConference.bigbluebutton;

import fr.openent.webConference.tiers.RoomProvider;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class BigBlueButton implements RoomProvider {
    private String host;
    private String apiEndpoint;
    private String secret;
    private String source;
    private String logoutURL;
    private static final Logger log = LoggerFactory.getLogger(BigBlueButton.class);
    private HttpClient httpClient;

    private static final Boolean END_NO_MODERATOR = true;
    private static Integer DELAY_END = 2;

    public static BigBlueButton newInstance(final Vertx vertx, final String source, final String appAddress, final JsonObject BBBConf) {
        BigBlueButton instance = new BigBlueButton();
        instance.setHost(vertx, BBBConf.getString("host", ""));
        instance.setSource(source);
        instance.setApiEndpoint(BBBConf.getString("api_endpoint", ""));
        instance.setSecret(BBBConf.getString("secret", ""));
        instance.setLogoutURL(BBBConf.getString("logoutURL", ""));

        log.info("[WebConference@BigBlueButton] Adding web hook");
        String webhookURL = source + appAddress + "/webhook";
        instance.addWebHook(webhookURL, event -> {
            if (event.isRight()) {
                log.info("[WebConference] Web hook added : " + webhookURL);
            } else {
                log.error("[WebConference] Failed to add web hook", event.left().getValue());
            }
        });
        return instance;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String getSource() {
        return this.source;
    }

    public void setHost(Vertx vertx, String host) {
        this.host = host;
        try {
            URI uri = new URI(host);
            HttpClientOptions opts = new HttpClientOptions()
                    .setDefaultHost(host)
                    .setDefaultPort("https".equals(uri.getScheme()) ? 433 : 80)
                    .setSsl("https".equals(uri.getScheme()))
                    .setKeepAlive(true)
                    .setVerifyHost(false)
                    .setTrustAll(true);
            this.httpClient = vertx.createHttpClient(opts);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setLogoutURL(String logoutURL) {
        this.logoutURL = logoutURL;
    }

    private String checksum(String value) {
        try (Formatter formatter = new Formatter()) {
            byte[] bytes = MessageDigest.getInstance("SHA-1").digest(value.getBytes());

            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("[WebConference@BigBlueButton] Failed to generate checksum", e);
            return "";
        }
    }

    private Document parseResponse(Buffer response) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(new String(response.getBytes())));
            Document xml = builder.parse(source);
            xml.getDocumentElement().normalize();
            return xml;
        } catch (ParserConfigurationException | IOException | SAXException e) {
            log.error("[WebConference@BigBlueButton] Failed to parse response", e);
            return null;
        }
    }

    private String encodeParams(String param) {
        String encodedName;
        try {
            encodedName = URLEncoder.encode(param, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("[WebConference@BigBlueButton] Failed to encode meeting name");
            encodedName = "";
        }

        return encodedName;
    }

    @Override
    public String getRedirectURL(String sessionID, String userDisplayName, String password) {
        String encodedName = encodeParams(userDisplayName);
        String parameters = "fullName=" + encodedName + "&meetingID=" + sessionID + "&password=" + password;
        String checksum = checksum(Actions.JOIN + parameters + this.secret);
        String url = this.host + this.apiEndpoint + "/" + Actions.JOIN + "?" + parameters + "&checksum=" + checksum;
        return url;
    }

    @Override
    public void create(String name, String meetingID, String roomID, String moderatorPW, String attendeePW, String structure, String locale, Boolean waitingRoom, String streamURL, Handler<Either<String, String>> handler) {
        String encodedName = encodeParams(name);
        String guestPolicy = waitingRoom ? "ASK_MODERATOR" : "ALWAYS_ACCEPT";

        String parameters = "name=" + encodedName + "&meetingID=" + meetingID + "&moderatorPW=" + moderatorPW + "&attendeePW=" + attendeePW + "&guestPolicy=" + guestPolicy + "&endWhenNoModerator=" + END_NO_MODERATOR + "&endWhenNoModeratorDelayInMinutes=" + DELAY_END;
        if (streamURL != null) {
            parameters += "&streamURL=" + encodeParams(streamURL);
        }
        if (!logoutURL.trim().isEmpty()) {
            parameters += "&logoutURL=" + encodeParams(this.logoutURL);
        }
        String checksum = checksum(Actions.CREATE + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;

        String url = this.host + this.apiEndpoint + "/" + Actions.CREATE + "?" + parameters;

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source)
                .addHeader("Client-Structure", structure)
                .addHeader("Client-Locale", locale)
                .addHeader("Client-Room", roomID);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> response.bodyHandler(body -> {
                    try {
                        Document res = parseResponse(body);
                        XPathFactory xpf = XPathFactory.newInstance();
                        XPath path = xpf.newXPath();
                        String returnCode = path.evaluate("/response/returncode", res.getDocumentElement());

                        if (!"SUCCESS".equals(returnCode) || response.statusCode() != 200) {
                            log.error("[WebConference@create] Error in creating session : " + body);
                            log.error("[WebConference@create] Response : " + response);
                            String messageKey = path.evaluate("/response/messageKey", res.getDocumentElement());
                            ErrorCode code = ErrorCode.get(messageKey);
                            switch (code) {
                                case TOO_MANY_SESSIONS_PER_STRUCTURE:
                                    handler.handle(new Either.Left<>(ErrorCode.TOO_MANY_SESSIONS_PER_STRUCTURE.code()));
                                    return;
                                case TOO_MANY_USERS:
                                    handler.handle(new Either.Left<>(ErrorCode.TOO_MANY_USERS.code()));
                                    return;
                                case TOO_MANY_SESSIONS:
                                    handler.handle(new Either.Left<>(ErrorCode.TOO_MANY_SESSIONS.code()));
                                    return;
                                default:
                                    handler.handle(new Either.Left<>("[WebConference@BigBlueButton] Failed to create meeting"));
                                    return;
                            }
                        }

                        String internalId = path.evaluate("/response/internalMeetingID", res.getDocumentElement());
                        if (internalId == null)
                            handler.handle(new Either.Left<>("[WebConference@BigBlueButton] No internal id"));
                        else handler.handle(new Either.Right<>(internalId));
                    } catch (XPathExpressionException | NullPointerException e) {
                        log.error("[WebConference@BigBlueButton] Failed to parse creation response", e);
                        handler.handle(new Either.Left<>(e.toString()));
                    }
                }))
                .onFailure(throwable -> {
                    log.error("[WebConference@BigBlueButton] Failed to create meeting. An error is catch by exception handler", throwable);
                    handler.handle(new Either.Left<>(throwable.toString()));
                });
    }

    @Override
    public void join(String url, Handler<Either<String, String>> handler) {
        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET);
        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    response.bodyHandler(body -> {
                        try {
                            if (response.statusCode() != 302) {
                                Document res = parseResponse(body);
                                XPathFactory xpf = XPathFactory.newInstance();
                                XPath path = xpf.newXPath();
                                String returnCode = path.evaluate("/response/returncode", res.getDocumentElement());

                                if (!"FOUND".equals(returnCode)) {
                                    String messageKey = path.evaluate("/response/messageKey", res.getDocumentElement());
                                    ErrorCode code = ErrorCode.get(messageKey);
                                    switch (code) {
                                        case TOO_MANY_SESSIONS_PER_STRUCTURE:
                                            handler.handle(new Either.Left<>(ErrorCode.TOO_MANY_SESSIONS_PER_STRUCTURE.code()));
                                            return;
                                        case TOO_MANY_USERS:
                                            handler.handle(new Either.Left<>(ErrorCode.TOO_MANY_USERS.code()));
                                            return;
                                        case TOO_MANY_SESSIONS:
                                            handler.handle(new Either.Left<>(ErrorCode.TOO_MANY_SESSIONS.code()));
                                            return;
                                        default:
                                            handler.handle(new Either.Left<>("[WebConference@BigBlueButton] Failed to check meeting joining"));
                                            return;
                                    }
                                }

                                log.error("[WebConference@BigBlueButton] Error in format joining check response");
                                handler.handle(new Either.Left<>("Error response format is not valid"));
                            }
                            handler.handle(new Either.Right<>("Redirection found."));
                        } catch (XPathExpressionException | NullPointerException e) {
                            log.error("[WebConference@BigBlueButton] Failed to parse joining check response", e);
                            handler.handle(new Either.Left<>(e.toString()));
                        }
                    });
                }).onFailure(throwable -> {
                    log.error("[WebConference@BigBlueButton] Failed to check meeting joining. An error is catch by exception handler", throwable);
                    handler.handle(new Either.Left<>(throwable.toString()));
                });
    }

    @Override
    public void end(String meetingId, String moderatorPW, Handler<Either<String, Boolean>> handler) {
        String parameters = "meetingID=" + meetingId + "&password=" + moderatorPW;
        String checksum = checksum(Actions.END + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        String url = this.host + this.apiEndpoint + "/" + Actions.END + "?" + parameters;

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        String message = "[WebConference@BigBlueButton] Failed to end meeting : " + meetingId;
                        log.error(message);
                        handler.handle(new Either.Left<>(message));
                    } else {
                        response.bodyHandler(body -> {
                            try {
                                Document res = parseResponse(body);
                                XPathFactory xpf = XPathFactory.newInstance();
                                XPath path = xpf.newXPath();
                                String returnCode = path.evaluate("/response/returncode", res.getDocumentElement());

                                if (!"SUCCESS".equals(returnCode)) {
                                    handler.handle(new Either.Left<>("[WebConference@BigBlueButton] Response is not SUCCESS"));
                                    return;
                                }

                                handler.handle(new Either.Right<>(true));
                            } catch (XPathExpressionException | NullPointerException e) {
                                log.error("[WebConference@BigBlueButton] Failed to parse end meeting response body");
                                handler.handle(new Either.Left<>(e.toString()));
                            }
                        });
                        response.exceptionHandler(throwable -> {
                            log.error("[WebConference@BigBlueButton] Failed to end meeting. An error is catch by exception handler. Meetind : " + meetingId, throwable);
                            handler.handle(new Either.Left<>(throwable.toString()));
                        });
                    }
                });
    }

    @Override
    public void startStreaming(String meetingId, Handler<Either<String, Boolean>> handler) {
        String parameters = "meetingID=" + meetingId;
        String checksum = checksum(Actions.START_STREAM + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        String url = this.host + this.apiEndpoint + "/" + Actions.START_STREAM + "?" + parameters;

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        String message = "[WebConference@BigBlueButton] Failed starting streaming with meeting id : " + meetingId;
                        log.error(message);
                        handler.handle(new Either.Left<>(message));
                    } else {
                        handler.handle(new Either.Right<>(true));
                    }
                });
    }

    @Override
    public void stopStreaming(String meetingId, Handler<Either<String, Boolean>> handler) {
        String parameters = "meetingID=" + meetingId;
        String checksum = checksum(Actions.STOP_STREAM + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        String url = this.host + this.apiEndpoint + "/" + Actions.STOP_STREAM + "?" + parameters;

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        String message = "[WebConference@BigBlueButton] Failed stopping streaming with meeting id : " + meetingId;
                        log.error(message);
                        handler.handle(new Either.Left<>(message));
                    } else {
                        handler.handle(new Either.Right<>(true));
                    }
                });
    }

    @Override
    public void isMeetingRunning(String meetingId, Handler<Either<String, Boolean>> handler) {
        String parameters = "meetingID=" + meetingId;
        String checksum = checksum(Actions.IS_MEETING_RUNNING + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        String url = this.host + this.apiEndpoint + "/" + Actions.IS_MEETING_RUNNING + "?" + parameters;

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        String message = "[WebConference@BigBlueButton] Failed check meeting status : " + meetingId;
                        log.error(message);
                        handler.handle(new Either.Left<>(message));
                    } else {
                        response.bodyHandler(body -> {
                            try {
                                Document res = parseResponse(body);
                                XPathFactory xpf = XPathFactory.newInstance();
                                XPath path = xpf.newXPath();
                                String returnCode = path.evaluate("/response/returncode", res.getDocumentElement());

                                if (!"SUCCESS".equals(returnCode)) {
                                    handler.handle(new Either.Left<>("[WebConference@BigBlueButton] Response is not SUCCESS"));
                                    return;
                                }

                                String running = path.evaluate("/response/running", res.getDocumentElement());
                                handler.handle(new Either.Right<>(Boolean.parseBoolean(running)));
                            } catch (XPathExpressionException | NullPointerException e) {
                                log.error("[WebConference@BigBlueButton] Failed to parse end meeting response body");
                                handler.handle(new Either.Left<>(e.toString()));
                            }
                        });
                        response.exceptionHandler(throwable -> {
                            log.error("[WebConference@BigBlueButton] Failed to check meeting status. An error is catch by exception handler. Meeting : " + meetingId, throwable);
                            handler.handle(new Either.Left<>(throwable.toString()));
                        });
                    }
                });
    }

    @Override
    public void addWebHook(String webhook, Handler<Either<String, String>> handler) {
        String parameter = "callbackURL=" + this.encodeParams(webhook);
        String checksum = checksum(Actions.CREATE_HOOK + parameter + this.secret);
        String url = this.host + this.apiEndpoint + "/" + Actions.CREATE_HOOK + "?" + parameter + "&checksum=" + checksum;
        log.info("[WebConference@BigBlueButton] web hook end point : " + url);

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        String message = "[WebConference@BigBlueButton] Failed to add webhook";
                        log.error(message);
                        handler.handle(new Either.Left<>(message));
                    } else {
                        response.bodyHandler(body -> {
                            try {
                                Document res = parseResponse(body);
                                XPathFactory xpf = XPathFactory.newInstance();
                                XPath path = xpf.newXPath();
                                String returnCode = path.evaluate("/response/returncode", res.getDocumentElement());

                                if (!"SUCCESS".equals(returnCode)) {
                                    handler.handle(new Either.Left<>("[WebConference@BigBlueButton] Response is not SUCCESS"));
                                    return;
                                }

                                String hookId = path.evaluate("/response/hookID", res.getDocumentElement());
                                handler.handle(new Either.Right<>(hookId));
                            } catch (XPathExpressionException | NullPointerException e) {
                                log.error("[WebConference@BigBlueButton] Failed to parse web hook response", e);
                                handler.handle(new Either.Left<>(e.toString()));
                            }
                        });
                        response.exceptionHandler(throwable -> {
                            log.error("[WebConference@BigBlueButton] Failed to register web hook", throwable);
                            handler.handle(new Either.Left<>(throwable.toString()));
                        });
                    }
                });
    }

    @Override
    public void getMeetingInfo(String meetingId, Handler<Either<String, JsonObject>> handler) {
        String parameters = "meetingID=" + meetingId;
        String checksum = checksum(Actions.GET_MEETING_INFO + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        String url = this.host + this.apiEndpoint + "/" + Actions.GET_MEETING_INFO + "?" + parameters;

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(url)
                .setMethod(HttpMethod.GET)
                .addHeader("Client-Server", this.source);

        httpClient.request(requestOptions)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        String message = "[WebConference@BigBlueButton] Failed get meeting infos : " + meetingId;
                        log.error(message);
                        handler.handle(new Either.Left<>(message));
                    } else {
                        response.bodyHandler(body -> {
                            try {
                                Document res = parseResponse(body);
                                XPathFactory xpf = XPathFactory.newInstance();
                                XPath path = xpf.newXPath();
                                String returnCode = path.evaluate("/response/returncode", res.getDocumentElement());

                                if (!"SUCCESS".equals(returnCode)) {
                                    handler.handle(new Either.Left<>("[WebConference@BigBlueButton] Response is not SUCCESS"));
                                    return;
                                }

                                String running = path.evaluate("/response/running", res.getDocumentElement());
                                String participantCount = path.evaluate("/response/participantCount", res.getDocumentElement());
                                String moderatorCount = path.evaluate("/response/moderatorCount", res.getDocumentElement());
                                handler.handle(new Either.Right<>(
                                        new JsonObject()
                                                .put("running", running)
                                                .put("participantCount", participantCount)
                                                .put("moderatorCount", moderatorCount)));
                            } catch (XPathExpressionException | NullPointerException | DecodeException e) {
                                log.error("[WebConference@BigBlueButton] Failed to parse get meeting info response", e);
                                handler.handle(new Either.Left<>(e.toString()));
                            }
                        });
                        response.exceptionHandler(throwable -> {
                            log.error("[WebConference@BigBlueButton] Failed to get meeting info. An error is catch by exception handler. Meeting : " + meetingId, throwable);
                            handler.handle(new Either.Left<>(throwable.toString()));
                        });
                    }
                });
    }
}
