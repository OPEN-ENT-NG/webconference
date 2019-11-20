package fr.openent.webConference;

import org.entcore.common.http.BaseServer;

public class WebConference extends BaseServer {

	public static final String view = "webconference.view";

	@Override
	public void start() throws Exception {
		super.start();

		addController(new WebConferenceController());
	}

}
