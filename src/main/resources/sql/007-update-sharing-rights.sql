INSERT INTO webconference.room_shares (
    SELECT member_id, resource_id, 'fr-openent-webConference-controller-RoomController|get'
    FROM webconference.room_shares
    WHERE action = 'fr-openent-webConference-controller-RoomController|initContribSharingRight'
);

INSERT INTO webconference.room_shares (
    SELECT member_id, resource_id, 'fr-openent-webConference-controller-RoomController|isMeetingRunning'
    FROM webconference.room_shares
    WHERE action = 'fr-openent-webConference-controller-RoomController|initContribSharingRight'
);

INSERT INTO webconference.room_shares (
    SELECT member_id, resource_id, 'fr-openent-webConference-controller-RoomController|getMeetingInfo'
    FROM webconference.room_shares
    WHERE action = 'fr-openent-webConference-controller-RoomController|initContribSharingRight'
);