package org.faboo.example.twitter.data;

import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.URLEntity;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class Tweet {

    private final User user;
    private final Set<Hashtag> hashtags;
    private final List<Map<String,Object>> mentionedUsers;
    private final Map<String,Object> props;
    private final Long id;
    private final Tweet quotedTweet;
    private final Tweet retweetedTweet;
    private final Long inReplyToTweetId;
    private final Long inReplyToUserId;
    private final Set<String> urls;
    private final String inReplyToUserScreenName;

    public Tweet(Status status) {
        user = new User(status.getUser());
        hashtags = Arrays.stream(status.getHashtagEntities())
                .map(HashtagEntity::getText)
                .map(String::toLowerCase)
                .map(Hashtag::new)
                .collect(Collectors.toUnmodifiableSet());

        mentionedUsers = Arrays.stream(status.getUserMentionEntities())
                .map(entity -> (Map<String,Object>) new HashMap<String, Object>(
                        Map.of("id", entity.getId(),
                                "screenName", entity.getScreenName())))
                .collect(Collectors.toList());

        props = new HashMap<>();

        urls = Arrays.stream(status.getURLEntities())
                .map(URLEntity::getExpandedURL)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        addToProps(status);
        id = status.getId();

        if (status.getQuotedStatus() != null) {
            quotedTweet = new Tweet(status.getQuotedStatus());
        } else {
            quotedTweet = null;
        }

        if (status.getRetweetedStatus() != null) {
            retweetedTweet = new Tweet(status.getRetweetedStatus());
        } else {
            retweetedTweet = null;
        }
        inReplyToTweetId = status.getInReplyToStatusId() == -1 ? null : status.getInReplyToStatusId();
        inReplyToUserId = status.getInReplyToUserId() == -1 ? null : status.getInReplyToUserId();
        inReplyToUserScreenName = status.getInReplyToScreenName();
    }


    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public List<Map<String, Object>> getMentionedUsers() {
        return mentionedUsers;
    }

    public List<String> getHashtagsTags() {
        return hashtags.stream().map(Hashtag::getName).collect(Collectors.toList());
    }

    public Set<String> getUrls() {
        return urls;
    }

    public Map<String, Object> getProps() {
        return Collections.unmodifiableMap(props);
    }

    public Tweet getQuotedTweet() {
        return quotedTweet;
    }

    public Tweet getRetweetedTweet() {
        return retweetedTweet;
    }

    public Long getInReplyToTweetId() {
        return inReplyToTweetId;
    }

    public String getInReplyToUserScreenName() {
        return inReplyToUserScreenName;
    }

    public Long getInReplyToUserId() {
        return inReplyToUserId;
    }

    private void addToProps(Status status) {
        addToMapIfNotNull("createdAt", status.getCreatedAt() == null ? null :
                status.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        addToMapIfNotNull("text", status.getText());
        props.put("isRetweet", status.isRetweet());
        props.put("favoriteCount", status.getFavoriteCount());
        props.put("retweetCount", status.getRetweetCount());
        addToMapIfNotNull("lang", status.getLang());
    }

    private void addToMapIfNotNull(String propName, Object value) {
        if (value != null) {
            props.put(propName, value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tweet tweet = (Tweet) o;
        return id.equals(tweet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
