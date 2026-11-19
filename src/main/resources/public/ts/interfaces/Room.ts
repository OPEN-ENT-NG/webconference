import {Rights, Shareable} from "entcore";
import {roomService} from "../services";
import {Mix} from "entcore-toolkit";

export type GuestLinkState = 'next' | 'current';

export interface IGuestLink {
    guest_link: string
    guest_link_state: GuestLinkState
}

export interface IRoom {
    id?: string
    name: string
    link?: string
    guest_link_enabled: boolean
    guest_link?: string
    guest_link_state?: GuestLinkState
    sessions?: number
    active_session?: string
    structure: string
    owner_id?: string
    collab?: boolean
    allow_waiting_room: boolean
    allow_streaming: boolean
    streaming_link: string
    streaming_key: string
    isStreaming: boolean
}

export class Room implements Shareable, IRoom {
    shared: any;
    owner: { userId: string; displayName: string };
    myRights: any;
    _id: string;

    id?: string;
    name: string;
    link?: string;
    guest_link_enabled: boolean;
    guest_link?: string;
    guest_link_state?: GuestLinkState;
    sessions?: number;
    active_session?: string;
    structure: string;
    owner_id?: string;
    collab?: boolean
    opener?: string;
    allow_waiting_room: boolean;
    allow_streaming: boolean;
    streaming_link: string;
    streaming_key: string;
    isStreaming: boolean;
    isLoadingStreaming: boolean;

    constructor(structure?: string) {
        this.id = '';
        this.name = '';
        this.link = null;
        this.guest_link_enabled = false;
        this.guest_link = null;
        this.guest_link_state = null;
        this.sessions = null;
        this.active_session = null;
        this.structure = structure ? structure : null;
        this.owner_id = null;
        this.collab = false;
        this.opener = '';
        this.allow_waiting_room = false;
        this.allow_streaming = false;
        this.isStreaming = false;
        this.isLoadingStreaming = false;
        this.streaming_link = '';
        this.streaming_key = '';
    }

    toJson(): Object {
        return {
            id: this.id,
            name: this.name,
            link: this.link,
            guest_link_enabled: this.guest_link_enabled,
            sessions: this.sessions,
            active_session: this.active_session,
            structure: this.structure,
            owner_id: this.owner_id,
            collab: this.collab,
            opener: this.opener,
            allow_waiting_room: this.allow_waiting_room,
            allow_streaming: this.allow_streaming,
            streaming_link: this.streaming_link,
            steaming_key: this.streaming_key
        }
    }

    generateShareRights = (): void => {
        this._id = this.id;
        this.owner = {userId: this.owner_id, displayName: this.name};
        this.myRights = new Rights<Room>(this);
    };
}

export class Rooms {
    all: Room[];

    sync = async (): Promise<void> => {
        this.all = [];
        try {
            let rooms: any = await roomService.list();
            this.all = Mix.castArrayAs(Room, rooms);
            await this.setResourceRights();
        } catch (e) {
            // notify.error(idiom.translate('formulaire.error.form.sync'));
            throw e;
        }
    };

    setResourceRights = async (): Promise<void> => {
        let dataRigths = await roomService.getAllMyRoomRights();
        let ids = this.all.map(room => room.id);
        for (let i = 0; i < ids.length; i++) {
            let roomId = ids[i];
            let rights = dataRigths.filter(right => right.resource_id === roomId).map(right => right.action);
            this.all.filter(room => room.id === roomId)[0].myRights = rights;
        }
    };
}