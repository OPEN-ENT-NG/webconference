package fr.openent.webConference;

import fr.openent.webConference.bigbluebutton.BigBlueButton;
import fr.openent.webConference.controller.RoomController;
import fr.openent.webConference.controller.WebConferenceController;
import fr.openent.webConference.controller.WebHookController;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.http.BaseServer;

public class WebConference extends BaseServer {

	public static final String view = "webconference.view";
	public static final String create = "webconference.create";

	public static final String DB_SCHEMA = "webconference";

	@Override
	public void start() throws Exception {
		super.start();
		EventBus eb = getEventBus(vertx);

		addController(new WebConferenceController());
		addController(new RoomController(eb, config));
		addController(new WebHookController());

		JsonObject BBBConf = config.getJsonObject("bigbluebutton", new JsonObject());
		BigBlueButton.getInstance()
				.setHost(vertx, BBBConf.getString("host", ""));
		BigBlueButton.getInstance().setSource(config.getString("host"));
		BigBlueButton.getInstance().setApiEndpoint(BBBConf.getString("api_endpoint", ""));
		BigBlueButton.getInstance()
				.setSecret(BBBConf.getString("secret", ""));

		log.info("[WebConference@WebConference] Adding web hook");
		String webhookURL = config.getString("host") + config.getString("app-address") + "/webhook";
		BigBlueButton.getInstance().addWebHook(webhookURL, event -> {
			if (event.isRight()) {
				log.info("[WebConference] Web hook added : " + webhookURL);
			} else {
				log.error("[WebConference] Failed to add web hook", event.left().getValue());
			}
		});
	}
}
