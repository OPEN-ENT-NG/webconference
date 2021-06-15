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
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;

public class WebConference extends BaseServer {

	public static String DB_SCHEMA;
	public static String GROUPS_TABLE;
	public static String MEMBERS_TABLE;
	public static String ROOM_TABLE;
	public static String ROOM_SHARES_TABLE;
	public static String SESSION_TABLE;
	public static String USERS_TABLE;

	public static final String VIEW_WORKFLOW = "webconference.view";
	public static final String CREATE_WORKFLOW = "webconference.create";

	public static final String CONTRIB_SHARING_RIGHT = "webconference.contrib";
	public static final String MANAGER_SHARING_RIGHT = "webconference.manager";

	public static final String CONTRIB_SHARING_BEHAVIOUR = "fr-openent-webConference-controller-RoomController|initContribSharingRight";
	public static final String MANAGER_SHARING_BEHAVIOUR = "fr-openent-webConference-controller-RoomController|initManagerSharingRight";


	@Override
	public void start() throws Exception {
		super.start();
		EventBus eb = getEventBus(vertx);
		EventStore eventStore = EventStoreFactory.getFactory().getEventStore(WebConference.class.getSimpleName());

		DB_SCHEMA = "webconference";
		GROUPS_TABLE = DB_SCHEMA + ".groups";
		MEMBERS_TABLE = DB_SCHEMA + ".members";
		ROOM_TABLE = DB_SCHEMA + ".room";
		ROOM_SHARES_TABLE = DB_SCHEMA + ".room_shares";
		SESSION_TABLE = DB_SCHEMA + ".session";
		USERS_TABLE = DB_SCHEMA + ".users";


		// Sharing configuration
		SqlConf roomConf = SqlConfs.createConf(RoomController.class.getName());
		roomConf.setSchema("webconference");
		roomConf.setTable("room");
		roomConf.setShareTable("room_shares");

		RoomController roomController = new RoomController(eb, config, eventStore);
		roomController.setShareService(new SqlShareService(DB_SCHEMA, "room_shares", eb, securedActions, null));
		roomController.setCrudService(new SqlCrudService(DB_SCHEMA, "room", "room_shares"));


		addController(new WebConferenceController(eventStore));
		addController(roomController);
		addController(new WebHookController());
		addController(new SynchroController());
		
		RoomProviderPool.getSingleton().init(vertx, eb, config);
	}
}
