import {appPrefix, ng} from 'entcore';
import http from 'axios';
import {IRoom} from '../interfaces'

export interface RoomService {
    list(): Promise<IRoom[]>;

    create(room: IRoom): Promise<IRoom>;

    update(room: IRoom): Promise<IRoom>;

    delete(room: IRoom): Promise<IRoom>;

    end(room: IRoom): Promise<void>;
}

export const RoomService = ng.service('RoomService', (): RoomService => ({
    async list(): Promise<IRoom[]> {
        const {data} = await http.get(`/${appPrefix}/rooms`);
        return data;
    },

    async create(room): Promise<IRoom> {
        const {data} = await http.post(`/${appPrefix}/rooms`, room);
        return data;
    },

    async update({name, id}): Promise<IRoom> {
        const {data} = await http.put(`/${appPrefix}/rooms/${id}`, {name});
        return data;
    },

    async delete({id}): Promise<IRoom> {
        await http.delete(`/${appPrefix}/rooms/${id}`);
        return;
    },

    async end({id}): Promise<void> {
        await http.get(`/${appPrefix}/rooms/${id}/end`);
        return;
    }
}));