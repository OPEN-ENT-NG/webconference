package fr.openent.webConference.tiers;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface RoomProvider {

	String getSource();

	void create(String name, String meetingID, String roomID, String moderatorPW, String attendeePW, String structure,
				String locale, Boolean waitingRoom, Handler<Either<String, String>> handler);

	void join(String url, Handler<Either<String, String>> handler);

	void end(String meetingId, String moderatorPW, Handler<Either<String, Boolean>> handler);

	void isMeetingRunning(String meetingId, Handler<Either<String, Boolean>> handler);

	String getRedirectURL(String sessionID, String userDisplayName, String password, Boolean guest);

	void addWebHook(String webhook, Handler<Either<String, String>> handler);

	void getMeetingInfo(String meetingId, Handler<Either<String, JsonObject>> handler);

}