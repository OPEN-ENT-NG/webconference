package fr.openent.webConference;

import fr.openent.webConference.controller.RoomController;
import fr.openent.webConference.controller.SynchroController;
import fr.openent.webConference.controller.WebConferenceController;
import fr.openent.webConference.controller.WebHookController;
import fr.openent.webConference.tiers.RoomProviderPool;
import io.vertx.core.eventbus.EventBus;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;

public class WebConference extends BaseServer {

	public static final String view = "webconference.view";
	public static final String create = "webconference.create";

	public static final String DB_SCHEMA = "webconference";
	
	@Override
	public void start() throws Exception {
		super.start();
		EventBus eb = getEventBus(vertx);

		EventStore eventStore = EventStoreFactory.getFactory().getEventStore(WebConference.class.getSimpleName());

		addController(new WebConferenceController(eventStore));
		addController(new RoomController(eb, config, eventStore));
		addController(new WebHookController());
		addController(new SynchroController());
		
		RoomProviderPool.getSingleton().init(vertx, eb, config);

	}
}
