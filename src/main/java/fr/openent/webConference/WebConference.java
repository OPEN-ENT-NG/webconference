package fr.openent.webConference;

import fr.openent.webConference.bigbluebutton.BigBlueButton;
import fr.openent.webConference.controller.RoomController;
import fr.openent.webConference.controller.WebConferenceController;
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

		JsonObject BBBConf = config.getJsonObject("bigbluebutton", new JsonObject());
		BigBlueButton.getInstance()
				.setHost(vertx, BBBConf.getString("host", ""));
		BigBlueButton.getInstance().setApiEndpoint(BBBConf.getString("api_endpoint", ""));
		BigBlueButton.getInstance()
				.setSecret(BBBConf.getString("secret", ""));
	}

}
