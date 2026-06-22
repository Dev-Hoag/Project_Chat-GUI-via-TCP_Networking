package com.chatapp.common;

public enum MessageType {
    LOGIN,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    MSG,
    PRIVATE_MSG,
    GROUP_MSG,
    USER_LIST,
    JOIN,
    LEAVE,
    SYSTEM,
    ERROR,
    DISCONNECT;

    public static MessageType fromString(String value) {
        if (value == null) {
            return ERROR;
        }

        try {
            return MessageType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ERROR;
        }
    }
}
