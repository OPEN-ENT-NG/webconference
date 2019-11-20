package fr.openent.webConference;

import fr.openent.webConference.controller.RoomController;
import fr.openent.webConference.controller.WebConferenceController;
import io.vertx.core.eventbus.EventBus;
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
	}

}
