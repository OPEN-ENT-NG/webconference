package fr.openent.webConference.bigbluebutton;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
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

public class BigBlueButton {
    private String host;
    private String apiEndpoint;
    private String secret;
    private static final Logger log = LoggerFactory.getLogger(BigBlueButton.class);
    private HttpClient httpClient;

    public static BigBlueButton getInstance() {
        return BigBlueButtonHolder.instance;
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

    private String checksum(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1").digest(value.getBytes());
            Formatter formatter = new Formatter();
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

    public String getRedirectURL(String sessionID, String userDisplayName, String password) {
        String encodedName = encodeParams(userDisplayName);
        String parameters = "fullName=" + encodedName + "&meetingID=" + sessionID + "&password=" + password;
        String checksum = checksum(Actions.JOIN + parameters + this.secret);
        String url = this.host + this.apiEndpoint + "/" + Actions.JOIN + "?" + parameters + "&checksum=" + checksum;
        return url;
    }

    public void create(String name, String meetingID, String moderatorPW, String attendeePW, Handler<Either<String, String>> handler) {
        String encodedName = encodeParams(name);
        String parameters = "name=" + encodedName + "&meetingID=" + meetingID + "&moderatorPW=" + moderatorPW + "&attendeePW=" + attendeePW;
        String checksum = checksum(Actions.CREATE + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        httpClient.getAbs(this.host + this.apiEndpoint + "/" + Actions.CREATE + "?" + parameters, response -> {
            if (response.statusCode() != 200) {
                String message = "[WebConference@BigBlueButton] Failed to create meeting";
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

                        String internalId = path.evaluate("/response/internalMeetingID", res.getDocumentElement());
                        if (internalId == null)
                            handler.handle(new Either.Left<>("[WebConference@BigBlueButton] No internal id"));
                        else handler.handle(new Either.Right<>(internalId));
                    } catch (XPathExpressionException | NullPointerException e) {
                        log.error("[WebConference@BigBlueButton] Failed to parse creation response", e);
                        handler.handle(new Either.Left<>(e.toString()));
                    }
                });
                response.exceptionHandler(throwable -> {
                    log.error("[WebConference@BigBlueButton] Failed to create meeting. An error is catch by exception handler", throwable);
                    handler.handle(new Either.Left<>(throwable.toString()));
                });
            }
        }).end();
    }

    public void end(String meetingId, String moderatorPW, Handler<Either<String, Boolean>> handler) {
        String parameters = "meetingID=" + meetingId + "&password=" + moderatorPW;
        String checksum = checksum(Actions.END + parameters + this.secret);
        parameters = parameters + "&checksum=" + checksum;
        httpClient.getAbs(this.host + this.apiEndpoint + "/" + Actions.END + "?" + parameters, response -> {
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
        }).end();
    }

    private static class BigBlueButtonHolder {
        private static final BigBlueButton instance = new BigBlueButton();

        private BigBlueButtonHolder() {
        }
    }

    public void addWebHook(String webhook, Handler<Either<String, String>> handler) {
        String parameter = "callbackURL=" + this.encodeParams(webhook);
        String checksum = checksum(Actions.CREATE_HOOK + parameter + this.secret);
        String url = this.host + this.apiEndpoint + "/" + Actions.CREATE_HOOK + "?" + parameter + "&checksum=" + checksum;
        log.info("[WebConference@BigBlueButton] web hook end point : " + url);
        this.httpClient.getAbs(url, response -> {
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
        }).end();
    }
}
