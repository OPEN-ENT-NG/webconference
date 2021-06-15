import { Behaviours } from 'entcore';

const rights = {
    resources: {
        contrib: {
            right: "fr-openent-webConference-controller-RoomController|initContribSharingRight"
        },
        manager: {
            right: "fr-openent-webConference-controller-RoomController|initManagerSharingRight"
        }
    },
    workflow: {}
};

Behaviours.register('web-conference', {
    rights: rights,

    resourceRights: function () {
        return ['contrib', 'manager'];
    }
});
