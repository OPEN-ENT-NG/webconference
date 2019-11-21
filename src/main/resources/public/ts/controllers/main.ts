import {ng, template} from 'entcore';
import {IRoom} from '../interfaces';

declare const window: any;

interface ViewModel {
	rooms: IRoom[]
	room: IRoom
	selectedRoom: IRoom
	lightbox: {
		show: boolean
	}

	createRoom(room: IRoom)

	startCurrentRoom()
}

export const mainController = ng.controller('MainController',
	['$scope', 'route', 'RoomService', function ($scope, route, RoomService) {
		const vm: ViewModel = this;
		vm.lightbox = {
			show: false
		};
		vm.rooms = [];
		vm.room = {
			name: ''
		};

		const loadRooms = () => RoomService.list().then(rooms => {
			vm.rooms = rooms;
			vm.selectedRoom = vm.rooms[0];
		});

		vm.createRoom = async (room: IRoom) => {
			const newRoom = await RoomService.create(room);
			vm.rooms = [...vm.rooms, newRoom];
			vm.room = {
				name: ''
			};

			$scope.safeApply();
		};

		vm.startCurrentRoom = () => {
			window.open(vm.selectedRoom.link);
		};

		loadRooms();
		template.open('main', 'main');

		$scope.safeApply = function () {
			let phase = $scope.$root.$$phase;
			if (phase !== '$apply' && phase !== '$digest') {
				$scope.$apply();
			}
		}

	}]);
