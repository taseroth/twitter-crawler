package org.faboo.example.twitter.service.twitter;

import java.util.Arrays;
import java.util.Optional;

enum Resource {

    FRIENDS("/friends/list"),
    FOLLOWERS("/followers/"),
    TWEETS("/statuses/user_timeline"),
    USERS_LOOKUP("/users/lookup"),
    SEARCH("/search/"),
    APP("/application/rate_limit_status");

    private final String path;

    Resource(String path) {
        this.path = path;
    }

    boolean matchesPath(String urlPath) {
        return urlPath.startsWith(path);
    }

    static Optional<Resource> getForPath(String urlPath) {
        return Arrays.stream(Resource.values())
                .filter(r -> r.matchesPath(urlPath))
                .findFirst();
    }
}
