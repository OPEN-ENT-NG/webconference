import {appPrefix, ng} from 'entcore';
import http from 'axios';
import {Room, Rooms} from '../interfaces'

export interface RoomService {
    list(): Promise<Rooms>;
    create(room: Room): Promise<Room>;
    update(room: Room): Promise<Room>;
    delete(room: Room): Promise<Room>;
    end(room: Room): Promise<void>;
    getAllMyRoomRights(): Promise<any>;
}

export const roomService: RoomService = {
    async list(): Promise<Rooms> {
        const {data} = await http.get(`/${appPrefix}/rooms`);
        return data;
    },

    async create(room): Promise<Room> {
        const {data} = await http.post(`/${appPrefix}/rooms`, room);
        return data;
    },

    async update({name, id, structure}): Promise<Room> {
        const {data} = await http.put(`/${appPrefix}/rooms/${id}`, {name, structure});
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

    async getAllMyRoomRights(): Promise<any> {
        const {data} = await http.get(`/${appPrefix}/rooms/rights/all`);
        return data;
    }
};

export const RoomService = ng.service('RoomService', (): RoomService => roomService);