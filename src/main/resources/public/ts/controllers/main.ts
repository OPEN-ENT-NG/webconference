import {idiom, model, ng, template} from 'entcore';
import {IRoom, IStructure} from '../interfaces';
import * as Clipboard from 'clipboard';

declare const window: any;

interface ViewModel {
	structures: Array<IStructure>
	structureMap: Map<String, String>
	rooms: IRoom[]
	room: IRoom
	selectedRoom: IRoom
	lightbox: {
		show: boolean
	}

	hasWorkflowZimbra(): boolean

	hasWorkflowMessagerie(): boolean

	createRoom(room: IRoom)

	updateRoom(room: IRoom)

	deleteRoom(room: IRoom)

	openRoomUpdate(room: IRoom)

	openRoomCreation()

	startCurrentRoom()

	endCurrentRoom()

	hasActiveSession(room: IRoom)

	refresh()
}

function processStructures(): Array<IStructure> {
	const structures: Array<IStructure> = [];
	model.me.structures.forEach((structure, index) => {
		structures.push({
			id: structure,
			name: model.me.structureNames[index]
		})
	});

	return structures;
}

function transformStructuresToMap(): Map<string, string> {
	const map: Map<string, string> = new Map();
	model.me.structures.forEach((structure, index) => {
		map.set(structure, model.me.structureNames[index]);
	});

	return map;
}

export const mainController = ng.controller('MainController',
	['$scope', 'route', 'RoomService', function ($scope, route, RoomService) {
		if (window.error && window.error === 'tooManyRooms') {
			idiom.addBundlePromise('/userbook/i18n').then($scope.$apply);
			model.me.workflow.load(['conversation', 'zimbra']);
		}

		const vm: ViewModel = this;
		vm.structures = processStructures();
		vm.structureMap = transformStructuresToMap();
		vm.lightbox = {
			show: false
		};

		vm.hasWorkflowZimbra = function () {
			return model.me.hasWorkflow('fr.openent.zimbra.controllers.ZimbraController|view');
		};

		vm.hasWorkflowMessagerie = function () {
			return model.me.hasWorkflow('org.entcore.conversation.controllers.ConversationController|view');
		};

		const openLightbox = () => vm.lightbox.show = true;
		const closeLightbox = () => vm.lightbox.show = false;
		const initEmptyRoom = () => ({name: '', structure: vm.structures[0].id});

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

		vm.hasActiveSession = (room) => room && 'active_session' in room && room.active_session !== null;

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

		vm.openRoomCreation = () => {
			vm.room = initEmptyRoom();
			openLightbox();
		}


		vm.updateRoom = async (room) => {
			const {name, id, structure} = await RoomService.update(room);
			vm.room = initEmptyRoom();
			vm.rooms.forEach(aRoom => {
				if (aRoom.id === id) {
					aRoom.name = name;
					aRoom.structure = structure;
				}
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

		vm.refresh = () => document.location.reload();

		$scope.safeApply = function () {
			let phase = $scope.$root.$$phase;
			if (phase !== '$apply' && phase !== '$digest') {
				$scope.$apply();
			}
		};

		vm.room = initEmptyRoom();
		loadRooms().then(() => {
			$scope.safeApply();
			new Clipboard('.clipboard-link-field');
		});
		template.open('main', 'main');
	}]);
