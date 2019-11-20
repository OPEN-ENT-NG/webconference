import {ng, routes} from 'entcore';
import * as controllers from './controllers';
import * as services from './services';

for(let controller in controllers){
    ng.controllers.push(controllers[controller]);
}

for (let service in services) {
	ng.services.push(services[service]);
}

routes.define(function($routeProvider){
	$routeProvider
		.otherwise({
			action: 'defaultView'
		});
});