package me.baonguyen;

/**
 * Created by bao on 01/11/2016.
 */

public class Message {
    private String mMessage;
    private String mFirstName;
    private String mLastName;
    private String mMessageId;
    private int mTimeStamp;

    private Message() {}

    public String getMessage() {
        return mMessage;
    };

    public String getMessageId() { return mMessageId; };

    public String getFullName() {
        return mFirstName + " " + mLastName;
    };

    public String getFirstName() { return mFirstName; };

    public String getLastName() { return mLastName; };

    public int getTimeStamp() { return  mTimeStamp; };


    public static class Builder {
        private String mFirstName;
        private String mLastName;
        private String mMessage;
        private String mMessageId;
        private int mTimeStamp;

        public Builder firstName(String firstName) {
            mFirstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            mLastName = lastName;
            return this;
        }

        public Builder message(String message) {
            mMessage = message;
            return this;
        }

        public Builder messageId(String messageId) {
            mMessageId = messageId;
            return this;
        }

        public Message build() {
            Message message = new Message();
            message.mFirstName = mFirstName;
            message.mLastName = mLastName;
            message.mMessageId = mMessageId;
            message.mMessage = mMessage;
            message.mTimeStamp = mTimeStamp;
            return message;
        }

        public Builder timeStamp(int timeStamp) {
            mTimeStamp = timeStamp;
            return this;
        }
    }
}
