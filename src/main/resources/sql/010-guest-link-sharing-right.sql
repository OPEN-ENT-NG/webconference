INSERT INTO webconference.room_shares (
    SELECT member_id, resource_id, 'fr-openent-webConference-controller-RoomController|regenerateGuestLink'
    FROM webconference.room_shares
    WHERE action = 'fr-openent-webConference-controller-RoomController|initManagerSharingRight'
);
