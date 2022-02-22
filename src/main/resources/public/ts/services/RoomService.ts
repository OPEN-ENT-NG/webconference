import {appPrefix, ng} from 'entcore';
import http from 'axios';
import {Room, Rooms} from '../interfaces'

export interface RoomService {
    list(): Promise<Rooms>;
    get(room: Room): Promise<Room>;
    create(room: Room, isPublic: boolean): Promise<Room>;
    update(room: Room, isPublic: boolean): Promise<Room>;
    delete(room: Room): Promise<Room>;
    end(room: Room): Promise<void>;
    isMeetingRunning(room: Room): Promise<boolean>;
    getMeetingInfo(room: Room): Promise<any>;
    startStream(room: Room): Promise<any>;
    stopStream(room: Room): Promise<any>;
    sendInvitation(room: Room, mail: {}): Promise<void>;
    getAllMyRoomRights(): Promise<any>;
}

export const roomService: RoomService = {
    async list(): Promise<Rooms> {
        const {data} = await http.get(`/${appPrefix}/rooms`);
        return data;
    },

    async get({id}): Promise<Room> {
        const {data} = await http.get(`/${appPrefix}/rooms/${id}`);
        return data;
    },

    async create(room, isPublic): Promise<Room> {
        const {data} = await http.post(`/${appPrefix}/rooms/${isPublic}`, room);
        return data;
    },

    async update(room, isPublic): Promise<Room> {
        const {data} = await http.put(`/${appPrefix}/rooms/${room.id}/${isPublic}`, room);
        return data;
    },

    async delete({id}): Promise<Room> {
        await http.delete(`/${appPrefix}/rooms/${id}`);
        return;
    },

    async end({id}): Promise<void> {
        await http.get(`/${appPrefix}/rooms/${id}/end`);
        return;
    },

    async isMeetingRunning({id}): Promise<boolean> {
        const {data} = await http.get(`/${appPrefix}/rooms/${id}/running`);
        return data;
    },

    async getMeetingInfo({id}): Promise<any> {
        const {data} = await http.get(`/${appPrefix}/rooms/${id}/meetingInfo`);
        return data;
    },

    async startStream({id}): Promise<any> {
        const {data} = await http.get(`/${appPrefix}/rooms/${id}/stream/start`);
        return data;
    },

    async stopStream({id}): Promise<any> {
        const {data} = await http.get(`/${appPrefix}/rooms/${id}/stream/stop`);
        return data;
    },

    async sendInvitation({id}, mail: {}) : Promise<void> {
        await http.post(`/${appPrefix}/rooms/${id}/invitation`, mail);
        return;
    },

    async getAllMyRoomRights(): Promise<any> {
        const {data} = await http.get(`/${appPrefix}/rooms/rights/all`);
        return data;
    }
};

export const RoomService = ng.service('RoomService', (): RoomService => roomService);