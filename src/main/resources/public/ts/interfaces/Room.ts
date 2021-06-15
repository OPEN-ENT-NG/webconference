import {Rights, Shareable} from "entcore";

export interface IRoom {
    id?: number
    name: string
    link?: string
    sessions?: number
    active_session?: string
    structure: string
    owner_id?: string
}

export class Room implements Shareable, IRoom  {
    shared: any;
    owner: { userId: string; displayName: string };
    myRights: any;
    _id: number;

    id?: number;
    name: string;
    link?: string;
    sessions?: number;
    active_session?: string;
    structure: string;
    owner_id?: string;

    constructor(structure?: string) {
        this.id = null;
        this.name = '';
        this.link = null;
        this.sessions = null;
        this.active_session = null;
        this.structure = structure ? structure : null;
        this.owner_id = null;
    }

    toJson() : Object {
        return {
            id: this.id,
            name: this.name,
            link: this.link,
            sessions: this.sessions,
            active_session: this.active_session,
            structure: this.structure,
            owner_id: this.owner_id
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
}