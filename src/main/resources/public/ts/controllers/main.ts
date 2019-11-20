import { ng } from 'entcore';

/**
	Wrapper controller
	------------------
	Main controller.
**/
export const mainController = ng.controller('MainController', ['$scope', 'route', function ($scope, route) {
	console.log('mainController');
}]);
