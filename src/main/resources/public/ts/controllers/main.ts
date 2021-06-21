import {idiom, model, ng, template, notify, Behaviours} from 'entcore';
import {IRoom, IStructure, Room, Rooms} from '../interfaces';
import * as Clipboard from 'clipboard';

declare const window: any;

interface ViewModel {
	structures: Array<IStructure>;
	structureMap: Map<String, String>;
	rooms: Rooms;
	room: Room;
	selectedRoom: Room;
	roomToShare: Room;
	lightbox: {
		room: boolean,
		sharing: boolean
	};

	hasWorkflowZimbra(): boolean;
	hasWorkflowMessagerie(): boolean;
	hasShareRightManager(room : Room): boolean;
	hasShareRightContrib(room : Room): boolean;
	openRoomLightbox(): void;
	closeRoomLightbox(): void;
	openSharingLightbox(room: Room): void;
	closeSharingLightbox(): void;
	createRoom(room: Room);
	updateRoom(room: Room);
	deleteRoom(room: Room);
	openRoomUpdate(room: Room);
	openRoomCreation();
	startCurrentRoom();
	endCurrentRoom();
	hasActiveSession(room: Room);
	refresh();
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
		if (window.error && window.error === 'tooManyRoomsPerStructure') {
			idiom.addBundlePromise('/userbook/i18n').then($scope.$apply);
			model.me.workflow.load(['conversation', 'zimbra']);
		}

		const vm: ViewModel = this;
		vm.structures = processStructures();
		vm.structureMap = transformStructuresToMap();
		vm.rooms = new Rooms();
		vm.selectedRoom = new Room();
		vm.roomToShare = new Room();
		vm.lightbox = {
			room: false,
			sharing: false
		};

		vm.hasWorkflowZimbra = function () {
			return model.me.hasWorkflow('fr.openent.zimbra.controllers.ZimbraController|view');
		};

		vm.hasWorkflowMessagerie = function () {
			return model.me.hasWorkflow('org.entcore.conversation.controllers.ConversationController|view');
		};

		vm.hasShareRightManager = (room : Room) => {
			return room.owner_id === model.me.userId || room.myRights.includes(Behaviours.applicationsBehaviours['web-conference'].rights.resources.manager.right);
		};

		vm.hasShareRightContrib = (room : Room) => {
			return room.owner_id === model.me.userId || room.myRights.includes(Behaviours.applicationsBehaviours['web-conference'].rights.resources.contrib.right);
		};

		vm.openRoomLightbox = () => {
			template.open('lightbox', 'room-creation');
			vm.lightbox.room = true;
		}
		vm.closeRoomLightbox = () => {
			template.close('lightbox');
			vm.lightbox.room = false;
		}
		vm.openSharingLightbox = (room: Room) => {
			vm.roomToShare = room;
			vm.roomToShare.generateShareRights();
			template.open('lightbox', 'room-sharing');
			vm.lightbox.sharing = true;
		}
		vm.closeSharingLightbox = () => {
			template.close('lightbox');
			vm.lightbox.sharing = false;
		}
		const initEmptyRoom = () => (new Room(vm.structures[0].id));

		const loadRooms = async () => {
			await vm.rooms.sync();
			vm.selectedRoom = vm.rooms.all[0];
		};

		vm.createRoom = async (room: Room) => {
			const newRoom = await RoomService.create(room);
			vm.rooms.all = [...vm.rooms.all, newRoom];
			vm.room = initEmptyRoom();
			if (vm.rooms.all.length === 1) vm.selectedRoom = vm.rooms.all[0];
			vm.closeRoomLightbox();

			$scope.safeApply();
		};

		vm.startCurrentRoom = async () => {
			vm.selectedRoom.sessions++;
			let result = window.open(vm.selectedRoom.link);
			result.window.onload = function () {
				if (result.error) {
					vm.selectedRoom.active_session = null;
					switch (result.error) {
						case "tooManyRoomsPerStructure": break;
						case "tooManyUsers": break;
						case "tooManyRooms": break;
						default: notify.error(idiom.translate('webconference.room.end.error')); break;
					}
				}
				$scope.safeApply();
			};
            vm.selectedRoom.active_session = '';
			vm.selectedRoom.opener = model.me.username;
			$scope.safeApply();
		};

		vm.hasActiveSession = (room) => room && 'active_session' in room && room.active_session !== null;

		vm.endCurrentRoom = async () => {
			try {
				await RoomService.end(vm.selectedRoom);
				delete vm.selectedRoom.active_session;
				$scope.safeApply();
			} catch (e) {
				notify.error(idiom.translate('webconference.room.end.error'));
				console.error(`Failed to end meeting ${vm.selectedRoom.id}`, vm.selectedRoom);
				throw e;
			}
		};

		vm.openRoomUpdate = (room) => {
			vm.room = room;
			vm.openRoomLightbox();
		};

		vm.openRoomCreation = () => {
			vm.room = initEmptyRoom();
			vm.openRoomLightbox();
		};


		vm.updateRoom = async (room) => {
			const {name, id, structure} = await RoomService.update(room);
			vm.room = initEmptyRoom();
			vm.rooms.all.forEach(aRoom => {
				if (aRoom.id === id) {
					aRoom.name = name;
					aRoom.structure = structure;
				}
			});
			vm.closeRoomLightbox();
			$scope.safeApply();
		};

		vm.deleteRoom = async (room) => {
			await RoomService.delete(room);
			vm.rooms.all = vm.rooms.all.filter(aRoom => room.id !== aRoom.id);
			if (vm.selectedRoom.id === room.id) vm.selectedRoom = vm.rooms.all[0];
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
			template.open('toaster', 'toaster');
			$scope.safeApply();
			let clipboard = new Clipboard('.clipboard-link-field');
			
			clipboard.on('success', function(e) {
				e.clearSelection();
				notify.info('copy.link.success');
			});
			
			clipboard.on('error', function(e) {
				notify.error('copy.link.error');
			});
		});
		template.open('main', 'main');
	}]);
