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

	updateRoom(room: IRoom)

	deleteRoom(room: IRoom)

	openRoomUpdate(room: IRoom)

	startCurrentRoom()

	endCurrentRoom()

	selectRoomHasActiveSession()
}

export const mainController = ng.controller('MainController',
	['$scope', 'route', 'RoomService', function ($scope, route, RoomService) {
		const vm: ViewModel = this;
		vm.lightbox = {
			show: false
		};

		const openLightbox = () => vm.lightbox.show = true;
		const closeLightbox = () => vm.lightbox.show = false;
		const initEmptyRoom = () => ({name: ''});

		const loadRooms = () => RoomService.list().then(rooms => {
			vm.rooms = rooms;
			vm.selectedRoom = vm.rooms[0];
		});

		vm.createRoom = async (room: IRoom) => {
			const newRoom = await RoomService.create(room);
			vm.rooms = [...vm.rooms, newRoom];
			vm.room = initEmptyRoom();
			if (vm.rooms.length === 1) vm.selectedRoom = vm.rooms[0];
			closeLightbox();

			$scope.safeApply();
		};

		vm.startCurrentRoom = () => {
			vm.selectedRoom.sessions++;
			window.open(vm.selectedRoom.link);
			vm.selectedRoom.active_session = '';
			$scope.safeApply();
		};

		vm.selectRoomHasActiveSession = () => 'active_session' in vm.selectedRoom && vm.selectedRoom.active_session !== null;

		vm.endCurrentRoom = async () => {
			try {
				await RoomService.end(vm.selectedRoom);
				delete vm.selectedRoom.active_session;
				$scope.safeApply();
			} catch (e) {
				console.error(`Failed to end meeting ${vm.selectedRoom.id}`, vm.selectedRoom);
				throw e;
			}
		};

		vm.openRoomUpdate = (room) => {
			vm.room = {...room};
			openLightbox();
		};


		vm.updateRoom = async (room) => {
			const {name, id} = await RoomService.update(room);
			vm.room = initEmptyRoom();
			vm.rooms.forEach(aRoom => {
				if (aRoom.id === id) aRoom.name = name;
			});
			closeLightbox();
			$scope.safeApply();
		};

		vm.deleteRoom = async (room) => {
			await RoomService.delete(room);
			vm.rooms = vm.rooms.filter(aRoom => room.id !== aRoom.id);
			if (vm.selectedRoom.id === room.id) vm.selectedRoom = vm.rooms[0];
			$scope.safeApply();
		};

		$scope.safeApply = function () {
			let phase = $scope.$root.$$phase;
			if (phase !== '$apply' && phase !== '$digest') {
				$scope.$apply();
			}
		};

		vm.room = initEmptyRoom();
		loadRooms().then($scope.safeApply);
		template.open('main', 'main');
	}]);
