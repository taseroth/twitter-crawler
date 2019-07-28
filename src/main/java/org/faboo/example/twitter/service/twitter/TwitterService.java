package org.faboo.example.twitter.service.twitter;

import com.google.common.collect.Lists;
import org.faboo.example.twitter.data.Tweet;
import org.faboo.example.twitter.data.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.PagableResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


public class TwitterService {

    private final static Logger log = LoggerFactory.getLogger(TwitterService.class);

    /**
     * maxim number of friends or followers to load. As we can only request 15 chunks of 200 users, we limit it to
     * 15 * 200 * 4 which is about 1h.
     */
    private static final int MAX_FF_PER_HOUR = 12000;

    private final Set<TwitterRequester> requesters = new HashSet<>();
    private int maxFFToLoad;

    private List<OAuth> access;

    public void setAccess(List<OAuth> access) {
        this.access = access;
    }

    public TwitterService() {

    }

    public void init() {
        log.info("nb. twitter accounts configured: {}", access.size());
        try {

            for (OAuth oAuth : access) {
                ConfigurationBuilder builder = new ConfigurationBuilder();
                builder.setDebugEnabled(true);

                builder.setOAuthConsumerKey(oAuth.getConsumerKey());
                builder.setOAuthConsumerSecret(oAuth.getConsumerSecret());
                builder.setOAuthAccessToken(oAuth.getAccessToken());
                builder.setOAuthAccessTokenSecret(oAuth.getAccessTokenSecret());
                Twitter twitter = new TwitterFactory(builder.build()).getInstance();
                requesters.add(new TwitterRequester(twitter, oAuth.getName()));
            }
        } catch (TwitterException e) {
            log.error("could not create TwitterRequester", e);
            throw new IllegalStateException(e);
        }
        maxFFToLoad = MAX_FF_PER_HOUR * requesters.size();
    }

    public Set<Tweet> search(Query query) {

        return queryAll( query);
    }

    public Set<Tweet> fetchTimeline(User user, long maxId) throws UserNotReadableException {
        Set<Tweet> tweets = new HashSet<>();
        Set<Tweet> result;
        do {
            result = null;
            while (result == null) {
                try {
                    result = getNextRequester(Resource.TWEETS).getUserTimeline(user.getId(), maxId);
                } catch (TwitterRequester.RetryLaterException e) {
                    //
                }
            }

            if (result.size() == 0) {
                return tweets;
            }
            tweets.addAll(result);
            maxId = getMinId(result) - 1;

        } while (result.size() >= 200);
        user.setTweetsLastScanned(LocalDateTime.now());
        return tweets;
    }

    public Set<Tweet> fetchTweets(Collection<Long> ids)  {

        if (ids.size() == 0) {
            return Collections.emptySet();
        }

        log.info("fetching {} tweets by id", ids.size());
        List<Long> asList = new ArrayList<>(ids);
        List<List<Long>> chunked = Lists.partition(asList, 100);
        return chunked.stream()
                .map(this::requestTweets)
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    public User fillFriendsAndFollowers(User user) throws UserNotReadableException {

        user = fillInFriends(user);
        user = fillInFollowers(user);
        user.setFfLastScanned(LocalDateTime.now());
        return user;
    }

    public User lookupUser(String screenName) throws UserNotReadableException {

        do {
            try {
                TwitterRequester requester = getNextRequester(Resource.USERS_LOOKUP);
                User user = requester.lookupUser(screenName);
                user.setLastScanned(LocalDateTime.now());
                return user;
            } catch (TwitterRequester.RetryLaterException e) {
                //
            }
        } while (true);
    }

    public User lookupUser(Long userId) throws UserNotReadableException {
        do {
            try {
                TwitterRequester requester = getNextRequester(Resource.USERS_LOOKUP);
                User user = requester.lookupUser(userId);
                user.setLastScanned(LocalDateTime.now());
                return user;
            } catch (TwitterRequester.RetryLaterException e) {
                //
            }
        } while (true);
    }

    private User fillInFollowers(User user) throws UserNotReadableException {

        if (user.getFollowersCount() == null) {
            // load first
            user = lookupUser(user.getId());
        }

        if (user.getFollowersCount() > maxFFToLoad) {
            // this would take over 1h, so skip
            return user;
        }
        
        long courser;
        TwitterRequester requester;
        PagableResponseList<twitter4j.User> response;

        log.info("filling in {} Followers of {}", user.getFollowersCount(), user.getScreenName());
        courser = -1;
        requester = getNextRequester(Resource.FOLLOWERS);
        do {
            response = null;
            while (response == null) {
                try {
                    response = requester.getFollowersOf(user, courser);
                } catch (TwitterRequester.RetryLaterException e) {
                    requester = getNextRequester(Resource.FOLLOWERS);
                }

            }
            user.setFollowers(response.stream().map(User::new).collect(Collectors.toList()));
            courser = response.getNextCursor();
            requester = getNextRequester(Resource.FOLLOWERS);
        } while (response.hasNext());
        return user;
    }

    private User fillInFriends(User user) throws UserNotReadableException {

        if (user.getFriendsCount() == null) {
            // load first
            user = lookupUser(user.getId());
        }
        
        if (user.getFriendsCount() > 12000) {
            // this would take over 1h, so skill
            return user;
        }

        log.info("filling in {} Friends of {}", user.getFriendsCount(), user.getScreenName());
        long courser = -1;
        TwitterRequester requester = getNextRequester(Resource.FRIENDS);
        PagableResponseList<twitter4j.User> response;
        do {
            response = null;
            while (response == null) {
                try {
                    response = requester.getFriendsOf(user, courser);
                } catch (TwitterRequester.RetryLaterException e) {
                    requester = getNextRequester(Resource.FRIENDS);
                }

            }
            user.setFriends(response.stream().map(User::new).collect(Collectors.toList()));
            courser = response.getNextCursor();
            requester = getNextRequester(Resource.FRIENDS);
        } while (response.hasNext());
        return user;
    }

    private Set<Tweet> queryAll(Query query) {

        Set<Tweet> tweets = new HashSet<>();
        Set<Tweet> result;
        do {
            result = getNextRequester(Resource.SEARCH).search(query);
            if (result.size() == 0) {
                return tweets;
            }
            tweets.addAll(result);
            query.setMaxId(getMinId(result) - 1);

        } while (result.size() >= query.getCount());
        return tweets;
    }

    private long getMinId(Collection<Tweet> tweets) {
        return tweets.stream().map(Tweet::getId).mapToLong(l -> l).min().orElseThrow();
    }

    // get the next requester. The one closest to rate limit reset is best suited.
    private TwitterRequester getNextRequester(Resource resource) {

        return  requesters.stream()
                .sorted((requester1, requester2) -> requester1.compareTo(resource, requester2))
                .filter(r -> r.isUsable(resource))
                .findFirst()
                .orElse(requesters.stream()
                        .min((requester1, requester2) -> requester1.compareTo(resource, requester2))
                        .orElseThrow(IllegalStateException::new));
    }

    private Set<Tweet> requestTweets(List<Long> idList) {

        Set<Tweet> tweets = null;
        while (tweets == null) {
            try {
                tweets = getNextRequester(Resource.TWEETS).getTweets(idList);
            } catch (TwitterRequester.RetryLaterException e) {
                //
            }
        }
        return tweets;
    }
}
