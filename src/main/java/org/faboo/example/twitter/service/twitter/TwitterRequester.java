package org.faboo.example.twitter.service.twitter;

import org.faboo.example.twitter.data.Tweet;
import org.faboo.example.twitter.data.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.*;

import java.util.*;
import java.util.stream.Collectors;

class TwitterRequester {

    private final static Logger log = LoggerFactory.getLogger(TwitterRequester.class);

    private Map<Resource,ResourceLimit> limits;

    private final Twitter twitter;
    private final String name;
    private int requestCount = 0;

    TwitterRequester(Twitter twitter, String name) throws TwitterException {

        log.info("constructing TwitterRequester for name: {} with screenName {}", name, twitter.getScreenName());
        this.twitter = twitter;
        this.name = name;
        limits = lookupRateLimit();
        log.info("Resource limits found: {}", limits);
    }

    Set<Tweet> search(Query query) {

        // can not use the Wrapper here, as twitter4j.QueryResult does not fit into the hierarchy :-(
        waitUntilUsable(Resource.SEARCH);

        QueryResult queryResult;
        try {
            log.debug("querying for {} with {}", query.getQueryString(), this);
            twitter4j.QueryResult result = twitter.search(query.getQuery());
            queryResult = new QueryResult(result);
            extractRateLimit(Resource.SEARCH, result.getRateLimitStatus());
        } catch (TwitterException e) {
            log.error("error requesting twitter data user:" + name, e);
            throw new IllegalStateException(e);
        }

        return queryResult.getTweets();
    }

    Set<Tweet> getTweets(Collection<Long> tweetIds) throws RetryLaterException {

        Wrapper<Status,Collection<Long>> w = new Wrapper<>();

        try {
            ResponseList<Status> response = w.wrap(Resource.TWEETS, tweetIds,
                    v -> twitter.tweets().lookup(toPrimitiveArray(v)));

            return response.stream()
                    .map(Tweet::new)
                    .collect(Collectors.toUnmodifiableSet());

        } catch (UserNotReadableException e) {
            log.warn("odd, when asking for tweets, we should not get this error code");
            return Collections.emptySet();
        }
    }

    PagableResponseList<twitter4j.User> getFriendsOf(User user, long cursor)
            throws RetryLaterException, UserNotReadableException {

        Wrapper<twitter4j.User,Long> w = new Wrapper<>();

        ResponseList<twitter4j.User> response = w.wrap(Resource.FRIENDS, user.getId(),
                v -> twitter.getFollowersList(v, cursor, 200));

        return (PagableResponseList<twitter4j.User>) response;
    }

    PagableResponseList<twitter4j.User> getFollowersOf(User user, long cursor)
            throws RetryLaterException, UserNotReadableException {

        Wrapper<twitter4j.User,Long> w = new Wrapper<>();

        ResponseList<twitter4j.User> response = w.wrap(Resource.FOLLOWERS, user.getId(),
                v -> twitter.getFollowersList(v, cursor, 200));

        return (PagableResponseList<twitter4j.User>) response;
    }

    User lookupUser(Long userId) throws UserNotReadableException, RetryLaterException {

        Wrapper<twitter4j.User,Long> w = new Wrapper<>();

        ResponseList<twitter4j.User> response = w.wrap(Resource.USERS_LOOKUP,
                userId, v -> twitter.lookupUsers(userId));

        return response.stream()
                .map(User::new).findFirst().orElseThrow(UserNotReadableException::new);
    }

        User lookupUser(String screenName) throws RetryLaterException, UserNotReadableException {

        Wrapper<twitter4j.User, String> w = new Wrapper<>();
        ResponseList<twitter4j.User> response = w.wrap(Resource.USERS_LOOKUP, screenName,
                v -> twitter.lookupUsers(screenName));

        return response.stream().map(User::new).findFirst().orElseThrow();
    }

    Set<Tweet> getUserTimeline(Long userId, long maxId) throws RetryLaterException, UserNotReadableException {

        Paging paging = new Paging();
        paging.setCount(200);
        if (maxId > 0) {
            paging.setMaxId(maxId);
        }
        Wrapper<Status,Long> w = new Wrapper<>();
        ResponseList<Status> response = w.wrap(Resource.TWEETS, userId, v -> twitter.getUserTimeline(v, paging));
        return response.stream()
                .map(Tweet::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<Resource,ResourceLimit> lookupRateLimit() {
        try {

            Map<String, RateLimitStatus> rateLimitStatus = new TreeMap<>(twitter.getRateLimitStatus());
            requestCount++;
            rateLimitStatus.forEach((key, value) -> {
                log.trace("\tresource: {}", key);
                log.trace("\t\tlimit={}, remaining={}, secTillReset={}",
                        value.getLimit(), value.getRemaining(), value.getSecondsUntilReset());
            });

            //noinspection OptionalGetWithoutIsPresent we actualy do a check via the filter()
            return rateLimitStatus.entrySet()
                    .stream()
                    .filter(e -> Resource.getForPath(e.getKey()).isPresent())
                    .map(e -> Map.entry(Resource.getForPath(e.getKey()).get(),
                            new ResourceLimit(Resource.getForPath(e.getKey()).get(), e.getValue()
                            )))
                    .distinct()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        } catch (TwitterException e) {
            throw new IllegalStateException("error requesting rate limit", e);
        }
    }

    private void disableResourceFor20Min(Resource resource) {
        ResourceLimit limit = limits.get(resource);
        log.warn("disabling requester {} for 20 minutes resource {}={}", name,limit.getResource().name(), limit);
        limit.disableFor20Min();
    }

    private void extractRateLimit(Resource resource, RateLimitStatus rateLimit) {
        ResourceLimit limit = limits.get(resource);
        limit.extractRateLimit(rateLimit);
        requestCount++;
    }

    private void waitUntilUsable(Resource resource) {
        if (!isUsable(resource)) {
            ResourceLimit limit = limits.get(resource);
            long secToWait = limit.getSecondsTillReset();
            log.info("rate limit for requester {} reached. {}", name, limit);
            try {
                Thread.sleep((secToWait) * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private long[] toPrimitiveArray(Collection<Long> tweetIds) {
        long[] ids = new long[tweetIds.size()];
        int index = 0;
        for (Long tweetId : tweetIds) {
            ids[index++] = tweetId;
        }
        return ids;
    }

    boolean isUsable(Resource resource) {
        return requestCount < 100000 && limits.get(resource).isUsable();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TwitterRequester that = (TwitterRequester) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    int compareTo(Resource resource, TwitterRequester requester2) {
        ResourceLimit limit = limits.get(resource);
        ResourceLimit limit2 = requester2.limits.get(resource);

        return limit.compare(limit, limit2);
    }

    @Override
    public String toString() {
        return "TwitterRequester{" +
                " name= '" + name + '\'' +
                ", requestCount = " + requestCount +
                ", limits = " + limits +
                '}';
    }

    static class RetryLaterException extends Exception {

    }

    // the twitter4j API needs a lot of repetitive work around a call. The CallTwitter interface and the Wrapper class
    // are helpers to wrap the actual call with what we need.

    public interface CallTwitter<T,R> {

        ResponseList<T> wrap(R arg) throws TwitterException;
    }

    public class Wrapper<T,R> {

        ResponseList<T> wrap(Resource resource, R value, CallTwitter<T,R> function)
                throws UserNotReadableException, RetryLaterException {
            waitUntilUsable(resource);
            try {
                log.debug("requesting {} for {} using {}", resource, value, this);
                ResponseList<T> response = function.wrap(value);
                extractRateLimit(resource, response.getRateLimitStatus());

                return response;

            } catch (TwitterException e) {
                if (e.getStatusCode() == 401) {
                    log.info("resource {} is protected", value);
                    limits.get(resource).decrementRequests();
                    throw new UserNotReadableException();
                }
                log.error("error looking up {}} {}", resource, limits.get(resource));
                log.error("encountered :", e);
                disableResourceFor20Min(resource);
                throw new RetryLaterException();
            }
        }
    }
}
