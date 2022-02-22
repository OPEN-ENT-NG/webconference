import {Rights, Shareable} from "entcore";
import {roomService} from "../services";
import {Mix} from "entcore-toolkit";

export interface IRoom {
    id?: string
    name: string
    link?: string
    public_link?: string
    sessions?: number
    active_session?: string
    structure: string
    owner_id?: string
    collab?: boolean
    allow_waiting_room : boolean
}

export class Room implements Shareable, IRoom  {
    shared: any;
    owner: { userId: string; displayName: string };
    myRights: any;
    _id: string;

    id?: string;
    name: string;
    link?: string;
    public_link?: string;
    sessions?: number;
    active_session?: string;
    structure: string;
    owner_id?: string;
    collab?: boolean
    opener?: string;
    allow_waiting_room:boolean;

    constructor(structure?: string) {
        this.id = '';
        this.name = '';
        this.link = null;
        this.public_link = null;
        this.sessions = null;
        this.active_session = null;
        this.structure = structure ? structure : null;
        this.owner_id = null;
        this.collab = false;
        this.opener = '';
        this.allow_waiting_room=false;
    }

    toJson() : Object {
        return {
            id: this.id,
            name: this.name,
            link: this.link,
            public_link: this.public_link,
            sessions: this.sessions,
            active_session: this.active_session,
            structure: this.structure,
            owner_id: this.owner_id,
            collab: this.collab,
            opener: this.opener,
            allow_waiting_room:this.allow_waiting_room,
        }
    }

    generateShareRights = () : void => {
        this._id = this.id;
        this.owner = {userId: this.owner_id, displayName: this.name};
        this.myRights = new Rights<Room>(this);
    };
}

export class Rooms {
    all: Room[];

    sync = async () : Promise<void> => {
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

    setResourceRights = async () : Promise<void> => {
        let dataRigths = await roomService.getAllMyRoomRights();
        let ids = this.all.map(room => room.id);
        for (let i = 0; i < ids.length; i++) {
            let roomId = ids[i];
            let rights = dataRigths.filter(right => right.resource_id === roomId).map(right => right.action);
            this.all.filter(room => room.id === roomId)[0].myRights = rights;
        }
    };
}