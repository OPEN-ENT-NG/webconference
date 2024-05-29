package fr.openent.webConference.tiers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.Promise;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import fr.openent.webConference.bigbluebutton.BigBlueButton;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

// Note : Could be a Verticle on is own.
public class RoomProviderPool {
	private static final RoomProviderPool singleton = new RoomProviderPool();
	public static final RoomProviderPool getSingleton() {
		return singleton;
	}
	
	private Vertx vertx;
	private EventBus eb;
    
	/**
	 * Map of <domain, RoomProvider> or <structure.externalId, RoomProvider>
	 * Will be read-only accessed after its initialization.
	 */
	private Map<String, RoomProvider> roomProviders = new HashMap<String, RoomProvider>();
	
	/**
	 * Initializes this pool with the given configuration.
	 *  
	 * @param vertx
	 * @param eb
	 * @param config
	 */
	public void init(final Vertx vertx, final EventBus eb, final JsonObject config) {
		this.vertx = vertx;
		this.eb = eb;
		
		final JsonArray  EMPTY_JSON_ARRAY  = new JsonArray();
		final JsonObject EMPTY_JSON_OBJECT = new JsonObject();
		
		try {
			/* Try reading multi-instance configurations like this :
				"bigbluebutton" : [{
				    "scope" : ["oneconnect.opendigitaleducation.com"],
				    "host": "",
				    "api_endpoint": "",
				    "secret": "" 
				  },{
				    "scope" : ["bb703ef0-7ba7-42ae-be17-920d5b45ac9a", "96d4d883-d58b-405c-89c7-c91a84e55952"],
				    "host": "",
				    "api_endpoint": "",
				    "secret": "" 
				  }]
			*/
			JsonArray BBBConfs = config.getJsonArray( "bigbluebutton", EMPTY_JSON_ARRAY );
			for( int i=0; i<BBBConfs.size(); i++ ) {
				JsonObject BBBConf = BBBConfs.getJsonObject(i);
				BigBlueButton instance = BigBlueButton.newInstance( 
						this.vertx, 
						config.getString("host"), 
						config.getString("app-address"), 
						BBBConf );
				 
				 JsonArray scopes = BBBConf.getJsonArray( "scope", EMPTY_JSON_ARRAY );
				 for( int j=0; j<scopes.size(); j++ ) {
					 String scope = scopes.getString( j );
					 roomProviders.put(scope, instance);
				 }
			}
		} catch (Exception e) {
			/* Try reading single-instance configuration like this :
			 	"bigbluebutton": {
					"host": "",
					"api_endpoint": "",
					"secret": "" 
				}
			*/
			BigBlueButton instance = BigBlueButton.newInstance( 
					this.vertx, 
					config.getString("host"), 
					config.getString("app-address"), 
					config.getJsonObject("bigbluebutton", EMPTY_JSON_OBJECT) );
			roomProviders.put("*", instance);
		}
	}
	
	/**
	 * Get the room provider instance associated to this HTTP request.
	 * @param request
	 * @return a room provider
	 */
    public Future<RoomProvider> getInstance( final HttpServerRequest request ) {
		Promise<RoomProvider> roomProvider = Promise.promise();
        UserUtils.getUserInfos(eb, request, user -> {
        	getInstance(request, user).onComplete(roomProvider::handle);
        });
        return roomProvider.future();
    }

    /**
     * Get the room provider instance associated to this user.
     * @param user
     * @return
     */
    public Future<RoomProvider> getInstance( final HttpServerRequest request, final UserInfos user ) {
		Promise<RoomProvider> roomProviderPromise = Promise.promise();
    	Future.all(
			getInstanceFromUser(user), 				// 1) Look for a RoomProvider dedicated to one of this user's structures.
			getInstanceFromDomain(request.host()),	// 2) Look for a RoomProvider dedicated to this domain.
			getDefaultInstance()					// 3) Look for a default RoomProvider
		).onComplete(handler -> {
			if( handler.succeeded() ) {
				CompositeFuture results = handler.result();
				for( int i=0; i<results.size(); i++ ) {
					if( results.resultAt(i)!=null) {
						roomProviderPromise.complete(results.resultAt(i));
						break;
					}
				}
			}
			roomProviderPromise.tryFail( handler.cause() );
		});
        return roomProviderPromise.future();
    }
    
    /** Retrieve the roomProvider for this User, or null if none can be found. */
    private Future<RoomProvider> getInstanceFromUser( final UserInfos user ) {
		Promise<RoomProvider> roomProviderPromise = Promise.promise();
		List<String> structures = user.getStructures();
		for( int i=0; structures!=null && i<structures.size(); i++ ) {
			RoomProvider instance = roomProviders.get( structures.get(i) );
			if( instance != null ) {
				roomProviderPromise.complete( instance );
				return roomProviderPromise.future();
			}
		}
		roomProviderPromise.complete( null );
		return roomProviderPromise.future();
    }
    
    /** Retrieve the roomProvider for this Domain, or null if none can be found. */
    private Future<RoomProvider> getInstanceFromDomain( final String domain ) {
		Promise<RoomProvider> roomProviderPromise = Promise.promise();
		roomProviderPromise.complete( roomProviders.get(domain) );
		return roomProviderPromise.future();
    }
    
    /** Retrieve any default roomProvider. */
    private Future<RoomProvider> getDefaultInstance() {
		Promise<RoomProvider> roomProviderPromise = Promise.promise();
		roomProviderPromise.complete( roomProviders.get("*") );
		return roomProviderPromise.future();
    }
}
