package org.faboo.example.twitter.data;

import java.time.LocalDateTime;
import java.util.Objects;

public class Hashtag {

    // the hashtag is stored without the '#'
    private final String name;

    private LocalDateTime lastScanned;

    private long lastTweetSeen;

    public Hashtag(String tag) {
        this.name = tag.toLowerCase().replaceAll("#", "");
    }

    public String getName() {
        return name;
    }

    public String getHashtag() {
        return "#" + name;
    }

    public LocalDateTime getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(LocalDateTime lastScanned) {
        this.lastScanned = lastScanned;
    }

    public long getLastTweetSeen() {
        return lastTweetSeen;
    }

    public void setLastTweetSeen(long lastTweetSeen) {
        this.lastTweetSeen = lastTweetSeen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hashtag hashTag = (Hashtag) o;
        return name.equals(hashTag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Hashtag{" +
                "name='" + name + '\'' +
                ", lastScanned=" + lastScanned +
                ", lastTweetSeen=" + lastTweetSeen +
                '}';
    }
}
