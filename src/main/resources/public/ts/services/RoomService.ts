import {appPrefix, ng} from 'entcore';
import http from 'axios';
import {IRoom} from '../interfaces'

export interface RoomService {
    list(): Promise<IRoom[]>;

    create(room: IRoom): Promise<IRoom>;
}

export const RoomService = ng.service('RoomService', (): RoomService => ({
    async list(): Promise<IRoom[]> {
        const {data} = await http.get(`/${appPrefix}/rooms`);
        return data;
    },

    async create(room): Promise<IRoom> {
        const {data} = await http.post(`/${appPrefix}/rooms`, room);
        return data;
    }
}));