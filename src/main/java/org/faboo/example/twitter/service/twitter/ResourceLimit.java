package org.faboo.example.twitter.service.twitter;

import twitter4j.RateLimitStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;

class ResourceLimit implements Comparator<ResourceLimit> {

    private int requestRemaining = 1;

    private LocalDateTime nextReset;

    private final Resource resource;

    ResourceLimit(Resource resource, RateLimitStatus rateLimit) {
        this.resource = resource;
        requestRemaining = rateLimit.getRemaining();
        nextReset = LocalDateTime.now().plusSeconds(rateLimit.getSecondsUntilReset() + 20);
    }

    void extractRateLimit(RateLimitStatus rateLimit) {
        if (rateLimit == null) {
            requestRemaining--;
            return; // something wrong in the twitter4j API
        }
        requestRemaining = rateLimit.getRemaining();
        nextReset = LocalDateTime.now().plusSeconds(rateLimit.getSecondsUntilReset() + 20);
    }

    boolean isUsable() {
        return isPastReset() || requestRemaining > 0;
    }

    private boolean isPastReset() {
        return LocalDateTime.now().isAfter(nextReset);
    }

    void disableFor20Min() {
        requestRemaining = 0;
        nextReset = LocalDateTime.now().plusMinutes(20);
    }

    long getSecondsTillReset() {
        return LocalDateTime.now().until(nextReset, ChronoUnit.SECONDS);
    }

    public int getRequestRemaining() {
        return requestRemaining;
    }

    public void setRequestRemaining(int requestRemaining) {
        this.requestRemaining = requestRemaining;
    }

    public LocalDateTime getNextReset() {
        return nextReset;
    }

    public void setNextReset(LocalDateTime nextReset) {
        this.nextReset = nextReset;
    }

    Resource getResource() {
        return resource;
    }

    @Override
    public int compare(ResourceLimit o1, ResourceLimit o2) {
        return o1.nextReset.compareTo(o2.nextReset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceLimit that = (ResourceLimit) o;
        return resource == that.resource;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource);
    }

    @Override
    public String toString() {
        return "{requestRemaining = " + requestRemaining +
                ", secondsTillReset = " +getSecondsTillReset() +
                ", nextReset = " + nextReset +
                '}';
    }

    void decrementRequests() {
        requestRemaining--;
    }
}
