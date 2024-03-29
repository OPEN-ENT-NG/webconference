import {Behaviours} from 'entcore';

const rights = {
    resources: {
        contrib: {
            right: "fr-openent-webConference-controller-RoomController|initContribSharingRight"
        },
        manager: {
            right: "fr-openent-webConference-controller-RoomController|initManagerSharingRight"
        }
    },
    workflow: {
        streaming: "fr.openent.webConference.controller.StreamController|createStreaming",
        create: "fr.openent.webConference.controller.RoomController|create"
    },
    share: {
        overrideDefaultActions: ['webconference.contrib']
    }
};

Behaviours.register('web-conference', {
    rights: rights,

    resourceRights: function () {
        return ['contrib', 'manager'];
    },

    share: function() {
        return rights.share;
    }
});
