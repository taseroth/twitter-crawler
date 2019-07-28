package org.faboo.example.twitter.service.twitter;

import org.faboo.example.twitter.data.Tweet;

import java.util.Set;
import java.util.stream.Collectors;

public class QueryResult {

    private final Set<Tweet> tweets;
    private final Long sinceId;
    private final Long maxId;

    QueryResult(twitter4j.QueryResult result) {
        this.tweets = result.getTweets()
                .stream()
                .map(Tweet::new)
                .collect(Collectors.toUnmodifiableSet());
        sinceId = result.getSinceId();

        maxId = result.getMaxId();
    }

    public Long getSinceId() {
        return sinceId;
    }

    public Long getMaxId() {
        return maxId;
    }

    Set<Tweet> getTweets() {
        return tweets;
    }


}
