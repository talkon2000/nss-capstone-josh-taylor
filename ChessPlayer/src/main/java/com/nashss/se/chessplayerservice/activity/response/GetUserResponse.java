package com.nashss.se.chessplayerservice.activity.response;

import com.nashss.se.chessplayerservice.dynamodb.models.User;

public class GetUserResponse {
    private final User user;

    private GetUserResponse(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    //CHECKSTYLE:OFF:Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;

        public Builder withUser(User user) {
            this.user = user;
            return this;
        }

        public GetUserResponse build() {
            return new GetUserResponse(user);
        }
    }
}
