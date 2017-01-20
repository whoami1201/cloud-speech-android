package me.baonguyen.models;


/**
 * Created by bao on 20/01/2017.
 */

public class Room {
    private String mRoomName;
    private String mSlug;
    private String mDescription;
    private int mTimeStamp;

    private Room() {}


    public String getRoomName() { return mRoomName; }

    public String getSlug() { return mSlug; }

    public String getDescription() { return mDescription; }

    public int getTimeStamp() { return mTimeStamp; }


    public static class Builder {
        private String mRoomName;
        private String mSlug;
        private String mDescription;
        private int mTimeStamp;


        public Builder setRoomName(String roomName) {
            mRoomName = roomName;
            return this;
        }

        public Builder setSlug(String slug) {
            mSlug = slug;
            return this;
        }

        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        public Builder setTimeStamp(int timeStamp) {
            mTimeStamp = timeStamp;
            return this;
        }

        public Room build() {
            Room room = new Room();
            room.mRoomName = mRoomName;
            room.mSlug = mSlug;
            room.mDescription = mDescription;
            room.mTimeStamp = mTimeStamp;
            return room;
        }

    }
}
