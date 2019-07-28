package org.faboo.example.twitter.data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class User {

    private final Map<String,Object> props;

    private final Long id;
    private final String screenName;

    private Set<User> friends = Collections.emptySet();
    private Set<User> followers = Collections.emptySet();

    public User(twitter4j.User user) {
        props = new HashMap<>();
        id = user.getId();
        screenName = user.getScreenName();
        addToProps(user);
    }

    public User(Map<String,Object> properties) {
        props = new HashMap<>();
        id = (Long)properties.get("id");
        screenName = (String)properties.get("screenName");
        props.putAll(properties);
    }

    public User setFriends(Collection<User> newFriends) {
        friends = new HashSet<>(newFriends);
        return this;
    }

    public User setFollowers(Collection<User> newFollowers) {
        followers = new HashSet<>(newFollowers);
        return this;
    }

    public List<Map<String, Object>> getFollowersAsMap() {
        return followers.stream()
                .map(
                        entity -> (Map<String,Object>) new HashMap<String, Object>(
                                Map.of("id", entity.getId(),
                                        "screenName", entity.getScreenName())))
                .collect(Collectors.toUnmodifiableList());
    }

    public Set<User> getFollowers() {
        return followers;
    }

    public List<Map<String, Object>> getFriendsAsMap() {
        return friends.stream()
                .map(
                        entity -> (Map<String,Object>) new HashMap<String, Object>(
                                Map.of("id", entity.getId(),
                                        "screenName", entity.getScreenName())))
                .collect(Collectors.toUnmodifiableList());
    }

    public Set<User> getFriends() {
        return friends;
    }

    public void setTweetsLastScanned(LocalDateTime tweetsLastScanned) {
        addToMapIfNotNull("tweetsLastScanned", tweetsLastScanned);
    }

    public void setFfLastScanned(LocalDateTime ffLastScanned) {
        addToMapIfNotNull("ffLastScanned", ffLastScanned);
    }

    public void setLastScanned(LocalDateTime lastScanned) {
        addToMapIfNotNull("lastScanned", lastScanned);
    }

    public boolean tweetsNeedRescan() {
        return checkIfOlderThan7Days("tweetsLastScanned");
    }

    public boolean needRescan() {
        return checkIfOlderThan7Days("lastScanned");
    }

    public boolean ffNeedRescan() {
        return checkIfOlderThan7Days("ffLastScanned");
    }

    private boolean checkIfOlderThan7Days(String property) {
        LocalDateTime last = (LocalDateTime) props.get(property);
        return last == null || Duration.between(last, LocalDateTime.now()).toDays() > 7;
    }

    public Map<String, Object> getProps() {
        return Collections.unmodifiableMap(props);
    }

    public Long getId() {
        return id;
    }

    public String getScreenName() {
        return screenName;
    }

    public Long getFollowersCount() {
        return (Long)props.get("followersCount");
    }

    public Long getFriendsCount() {
        return (Long)props.get("friendsCount");
    }

    private void addToProps(twitter4j.User user) {
        props.put("id", user.getId());
        props.put("screenName", user.getScreenName()); // never null
        props.put("followersCount", (long) user.getFollowersCount());
        props.put("friendsCount", (long)user.getFriendsCount());
        props.put("tweetCount", (long)user.getStatusesCount());

        addToMapIfNotNull("name", user.getName());
        addToMapIfNotNull("location", user.getLocation());
        addToMapIfNotNull("createdAt", user.getCreatedAt() == null ? null :
                user.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        addToMapIfNotNull("timeZone", user.getTimeZone());
        addToMapIfNotNull("lang", user.getLang());
        addToMapIfNotNull("verified", user.isVerified());
        addToMapIfNotNull("profileImageURL", user.getMiniProfileImageURLHttps());
        addToMapIfNotNull("location", user.getLocation());
        addToMapIfNotNull("description", user.getDescription());
        addToMapIfNotNull("isProtected", user.isProtected());
    }

    private void addToMapIfNotNull(String propName, Object value) {
        if (value != null) {
            props.put(propName, value);
        }
    }

    public void setProtected(boolean isProtected) {
         props.put("isProtected", isProtected);
    }

    public boolean isProtected() {
        return (boolean)props.getOrDefault("isProtected", false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", screenName='" + screenName + '\'' +
                '}';
    }

}
