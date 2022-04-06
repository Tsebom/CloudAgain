package com.cloud.server;

import java.io.Serializable;

public class MessageStore implements Serializable {
    private final String message;

    public MessageStore(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
