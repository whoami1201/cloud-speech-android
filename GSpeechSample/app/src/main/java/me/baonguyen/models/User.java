package me.baonguyen.models;

/**
 * Created by bao on 22/01/2017.
 */

public class User {
    private String mUsername;
    private String mFirstName;
    private String mLastName;
    private String mUserId;

    private User() {}

    public String getUsername() { return mUsername; }

    public String getFirstName() { return mFirstName; }

    public String getLastName() { return mLastName; }

    public String getFullName() { return mFirstName + " " + mLastName; }

    public String getUserId() { return mUserId; }


    public static class Builder {
        private String mUsername;
        private String mFirstName;
        private String mLastName;
        private String mUserId;


        public Builder setUsername(String username) {
            mUsername = username;
            return this;
        }

        public Builder setFirstName(String firstName) {
            mFirstName = firstName;
            return this;
        }

        public Builder setLastName(String lastName) {
            mLastName = lastName;
            return this;
        }

        public Builder setUserId(String userId) {
            mUserId = userId;
            return this;
        }

        public User build() {
            User user = new User();
            user.mUsername = mUsername;
            user.mLastName = mLastName;
            user.mFirstName = mFirstName;
            user.mUserId = mUserId;
            return user;
        }

    }
}
