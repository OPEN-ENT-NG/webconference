package fr.openent.webConference.tiers;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;

public interface RoomProvider {

	String getSource();

	void create(String name, String meetingID, String moderatorPW, String attendeePW, String structure, String locale,
			Handler<Either<String, String>> handler);

	void end(String meetingId, String moderatorPW, Handler<Either<String, Boolean>> handler);

	void isMeetingRunning(String meetingId, Handler<Either<String, Boolean>> handler);

	String getRedirectURL(String sessionID, String userDisplayName, String password);

	void addWebHook(String webhook, Handler<Either<String, String>> handler);

}