import {idiom, model, ng, template, notify, Behaviours, appPrefix} from 'entcore';
import {IStructure, Room, Rooms} from '../interfaces';
import * as Clipboard from 'clipboard';
import {roomService} from "../services";
import http from "axios";

declare const window: any;

interface ViewModel {
	structures: Array<IStructure>;
	structureMap: Map<String, String>;
	rooms: Rooms;
	room: Room;
	selectedRoom: Room;
	roomToShare: Room;
	roomToInvit: Room;
	allowPublicLink: boolean
	meetingInfo: {
		running: boolean,
		participantCount: number,
		moderatorCount: number
	};
	lightbox: {
		room: boolean,
		end: boolean,
		sharing: boolean,
		invitation: boolean,
		delete: boolean
	};
	mail: {
		link: string,
		subject: string,
		body: string,
		invitees: string[]
	};

	hasWorkflowZimbra(): boolean;
	hasWorkflowMessagerie(): boolean;
	hasShareRightManager(room : Room): boolean;
	hasShareRightContrib(room : Room): boolean;

	openRoomLightbox(): void;
	closeRoomLightbox(): void;
	openEndLightbox(room: Room): void;
	closeEndLightbox(): void;
	openSharingLightbox(room: Room): void;
	closeSharingLightbox(): void;
	openInvitationLightbox(): void;
	closeInvitationLightbox(): void;
	openDeleteLightbox(room: Room): void;
	closeDeleteLightbox(): void;

	openRoomUpdate(room: Room);
	openRoomCreation();
	createRoom(room: Room, isPublic: boolean);
	updateRoom(room: Room, isPublic: boolean);
	deleteRoom(room: Room);
	sendInvitation(room: Room);
	startCurrentRoom();
	checkEndCurrentRoom(room: Room);
	endCurrentRoom();

	updateInvitees(data: any[]): Promise<void>;
	hasActiveSession(room: Room): boolean;
	isRunning(room: Room): Promise<boolean>;
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
		vm.roomToInvit = new Room();
		vm.allowPublicLink = false;
		vm.meetingInfo = {running: false, participantCount: null, moderatorCount: null};
		vm.lightbox = {
			room: false,
			end: false,
			sharing: false,
			invitation: false,
			delete: false
		};
		vm.mail = {
			link: "",
			subject: "",
			body: "",
			invitees: []
		};

		const init = async () => {
			checkAllowPublicLink().then(() => {
				$scope.safeApply();
			});

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
		}

		// Rights functions

		vm.hasWorkflowZimbra = function () {
			return model.me.hasWorkflow('fr.openent.zimbra.controllers.ZimbraController|view');
		};

		vm.hasWorkflowMessagerie = function () {
			return model.me.hasWorkflow('org.entcore.conversation.controllers.ConversationController|view');
		};

		vm.hasShareRightManager = (room : Room) => {
			return room.owner_id === model.me.userId || (room.myRights && room.myRights.includes(Behaviours.applicationsBehaviours['web-conference'].rights.resources.manager.right));
		};

		vm.hasShareRightContrib = (room : Room) => {
			return room.owner_id === model.me.userId || (room.myRights && room.myRights.includes(Behaviours.applicationsBehaviours['web-conference'].rights.resources.contrib.right));
		}

		// Lightbox functions

		vm.openRoomLightbox = () => {
			template.open('lightbox', 'room-creation');
			vm.lightbox.room = true;
		};

		vm.closeRoomLightbox = () => {
			template.close('lightbox');
			vm.lightbox.room = false;
		};

		vm.openEndLightbox = async (room: Room) => {
			template.open('lightbox', 'room-end');
			vm.lightbox.end = true;
		};

		vm.closeEndLightbox = () => {
			template.close('lightbox');
			vm.lightbox.end = false;
		};

		vm.openSharingLightbox = (room: Room) => {
			vm.roomToShare = room;
			vm.roomToShare.generateShareRights();
			template.open('lightbox', 'room-sharing');
			vm.lightbox.sharing = true;
		};

		vm.closeSharingLightbox = () => {
			template.close('lightbox');
			vm.lightbox.sharing = false;
			window.setTimeout(async function () {
				await vm.rooms.sync();
				$scope.safeApply();
			}, 100);
		};

		vm.openInvitationLightbox = () => {
			initMail();
			vm.roomToInvit = vm.selectedRoom;
			vm.roomToInvit.generateShareRights();
			template.open('lightbox', 'room-invitation');
			vm.lightbox.invitation = true;
			// Set CSS to show text on editor
			window.setTimeout(async function () {
				document.getElementsByClassName('edumedia')[0].remove();
				let toolbar = document.getElementsByTagName('editor-toolbar')[0] as HTMLElement;
				let editor = document.getElementsByTagName('editor')[0] as HTMLElement;
				let text = document.getElementsByClassName('drawing-zone')[0] as HTMLElement;
				editor.style.setProperty('padding-top', `${toolbar.offsetHeight.toString()}px`, "important");
				text.style.setProperty('min-height', `150px`, "important");
			}, 500);
			$scope.safeApply();

		};

		vm.closeInvitationLightbox = () => {
			template.close('lightbox');
			vm.lightbox.invitation = false;
		};

		vm.openDeleteLightbox = (room) => {
			template.open('lightbox', 'room-delete');
			vm.lightbox.delete = true;
		};

		vm.closeDeleteLightbox = () => {
			template.close('lightbox');
			vm.lightbox.delete = false;
		};

		// Other functions

		const initEmptyRoom = () => (new Room(vm.structures[0].id));

		const loadRooms = async () => {
			await vm.rooms.sync();
			vm.selectedRoom = vm.rooms.all[0];
		};

		vm.openRoomUpdate = (room) => {
			vm.room = room;
			vm.openRoomLightbox();
		};

		vm.openRoomCreation = () => {
			vm.room = initEmptyRoom();
			vm.openRoomLightbox();
		};

		vm.createRoom = async (room: Room, isPublic: boolean = false) => {
			const { id } = await RoomService.create(room, isPublic);
			await vm.rooms.sync();
			vm.room = initEmptyRoom();
			vm.selectedRoom = vm.rooms.all[vm.rooms.all.map(r => r.id).indexOf(id)];
			vm.closeRoomLightbox();

			$scope.safeApply();
		};

		vm.updateRoom = async (room, isPublic: boolean) => {
			const { name, id, structure, public_link } = await RoomService.update(room, isPublic);
			vm.room = initEmptyRoom();
			vm.rooms.all.forEach(aRoom => {
				if (aRoom.id === id) {
					aRoom.name = name;
					aRoom.structure = structure;
					aRoom.public_link = public_link;
				}
			});
			vm.closeRoomLightbox();
			$scope.safeApply();
		};

		vm.deleteRoom = async (room) => {
			await RoomService.delete(room);
			vm.rooms.all = vm.rooms.all.filter(aRoom => room.id !== aRoom.id);
			if (vm.selectedRoom.id === room.id) vm.selectedRoom = vm.rooms.all[0];
			vm.closeDeleteLightbox();
			$scope.safeApply();
		};

		vm.sendInvitation = async (room) => {
			if (!!!vm.mail.invitees || vm.mail.invitees.length <= 0) {
				notify.error(idiom.translate('webconference.invitation.error.invitees'));
			}
			else if (!!!vm.mail.subject) {
				notify.error(idiom.translate('webconference.invitation.error.subject'));
			}
			else if (!!!vm.mail.body || !vm.mail.body.includes(vm.mail.link)) {
				notify.error(idiom.translate('webconference.invitation.error.link'));
			}
			else {
				await roomService.sendInvitation(room, vm.mail);
				notify.success(idiom.translate('webconference.invitation.success'));
				initMail();
				vm.closeInvitationLightbox();
			}
		};

		vm.startCurrentRoom = async () => {
			let isRunning = await vm.isRunning(vm.selectedRoom);
			if (isRunning && !vm.hasActiveSession(vm.selectedRoom)) {
				let tempId = vm.selectedRoom.id;
				notify.info(idiom.translate('webconference.room.join.opened'));
				window.setTimeout(async function () {
					await vm.rooms.sync();
					vm.selectedRoom = vm.rooms.all.filter(r => r.id === tempId)[0];
					$scope.safeApply();
				}, 1000);
			}
			else {
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
			}
		};

		vm.checkEndCurrentRoom = async (room) => {
			let isRunning = await vm.isRunning(vm.selectedRoom);
			if (!isRunning) {
				let tempId = vm.selectedRoom.id;
				notify.info(idiom.translate('webconference.room.end.ended'));
				window.setTimeout(async function () {
					await vm.rooms.sync();
					vm.selectedRoom = vm.rooms.all.filter(r => r.id === tempId)[0];
					$scope.safeApply();
				}, 1000);
			}
			else {
				vm.meetingInfo = await RoomService.getMeetingInfo(room);
				vm.openEndLightbox(room);
			}
		}

		vm.endCurrentRoom = async () => {
			try {
				await RoomService.end(vm.selectedRoom);
				delete vm.selectedRoom.active_session;
				delete vm.selectedRoom.opener;
				vm.closeEndLightbox();
				$scope.safeApply();
			} catch (e) {
				notify.error(idiom.translate('webconference.room.end.error'));
				console.error(`Failed to end meeting ${vm.selectedRoom.id}`, vm.selectedRoom);
				throw e;
			}
		};

		vm.updateInvitees = async (data) : Promise<void> => {
			for (let item of data) {
				if (item.type == 'sharebookmark') {
					vm.mail.invitees = vm.mail.invitees.concat(item.users.map(item => item.id));
					vm.mail.invitees = vm.mail.invitees.concat(item.groups.map(item => item.id));
				}
				else {
					vm.mail.invitees.push(item.id);
				}
			}
		};

		const initMail = () : void => {
			vm.mail.link = `${window.location.origin}${window.location.pathname}/rooms/${vm.selectedRoom.id}/join`;
			vm.mail.subject = idiom.translate('webconference.invitation.default.subject');
			vm.mail.body = $scope.getI18nWithParams('webconference.invitation.default.body', [vm.mail.link, vm.mail.link]);
			vm.mail.invitees = [];
		};

		// Utils

		vm.hasActiveSession = (room) : boolean => room && 'active_session' in room && room.active_session !== null;

		vm.isRunning = async (room) : Promise<boolean> => {
			let {running} = await RoomService.isMeetingRunning(vm.selectedRoom);
			return running;
		}

		vm.refresh = () => document.location.reload();

		$scope.getI18nWithParams = (key: string, params: string[]) : string => {
			let finalI18n = idiom.translate(key);
			for (let i = 0; i < params.length; i++) {
				finalI18n = finalI18n.replace(`{${i}}`, params[i]);
			}
			return finalI18n;
		};

		$scope.safeApply = function () {
			let phase = $scope.$root.$$phase;
			if (phase !== '$apply' && phase !== '$digest') {
				$scope.$apply();
			}
		};

		const checkAllowPublicLink = async function () {
			const { data } = await http.get(`/${appPrefix}/allowsPublic`);
			vm.allowPublicLink = data["allow_public_link"];
		}

		init();
	}]);
