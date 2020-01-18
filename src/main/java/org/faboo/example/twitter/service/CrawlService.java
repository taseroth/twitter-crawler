package org.faboo.example.twitter.service;

import com.google.common.collect.Lists;
import org.faboo.example.twitter.data.Hashtag;
import org.faboo.example.twitter.data.Tweet;
import org.faboo.example.twitter.data.User;
import org.faboo.example.twitter.service.twitter.Query;
import org.faboo.example.twitter.service.twitter.TwitterService;
import org.faboo.example.twitter.service.twitter.UserNotReadableException;
import org.faboo.example.twitter.util.ResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class CrawlService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final TwitterService twitterService;
    private final Database database;
    private final ForkJoinResolver urlResolver;

    private final Set<Long> treeScanned = new HashSet<>();

    private int maxDepth = 3;

    public CrawlService(TwitterService twitterService, Database database, ForkJoinResolver forkJoinResolver)
            throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        this.twitterService = twitterService;
        this.database = database;
        urlResolver = forkJoinResolver;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (args.containsOption("hash")) {
            args.getOptionValues("hash").forEach(tag -> queryForHashtag(new Hashtag(tag)));
        }

        if (args.containsOption("depth")) {
            maxDepth = args.getOptionValues("depth").stream()
                    .map(Integer::parseInt)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("depth needs value"));
        }

        log.info("max depth set to {}", maxDepth);

        if (args.containsOption("follow-user")) {
            String screenName = args.getOptionValues("follow-user").stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("follow-user need value"));
            User user = database.getUserByScreenName(screenName)
                    .orElseGet(() -> {
                        try {
                            var u = twitterService.lookupUser(screenName);
                            database.persistUsers(Collections.singleton(u));
                            return u;
                        } catch (UserNotReadableException e) {
                            throw new IllegalArgumentException("user is private, can't start here");
                        }

                    });
            followUser(user, 0);
           // followUser(user, 0, false);

            log.info("scanned {} users", treeScanned.size());
        }

        if (args.containsOption("follow-user-hashtags")) {
            String screenName = args.getOptionValues("follow-user-hashtags").stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("follow-user-hashtags need value"));
            User user = database.getUserByScreenName(screenName)
                    .orElseGet(() -> {
                        try {
                            var u = twitterService.lookupUser(screenName);
                            database.persistUsers(Collections.singleton(u));
                            return u;
                        } catch (UserNotReadableException e) {
                            throw new IllegalArgumentException("user is private, can't start here");
                        }

                    });
            queryTopHashtagForUser(user);
        }

        if (args.containsOption("follow-hashtag")) {
            String startHash = args.getOptionValues("follow-hashtag").stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("follow-hashtag need value"));
            Optional<String> nextHashtagToScan = database.getNextHashtagToScan(startHash, maxDepth);
            while (nextHashtagToScan.isPresent()) {
                queryForHashtag(new Hashtag(nextHashtagToScan.get()));
                nextHashtagToScan = database.getNextHashtagToScan(startHash, maxDepth);
            }
        }
        if (args.containsOption("hydrate-tweets")) {
            hydrateTweets();
        }
        log.info("done crawling");

    }

    /**
     * Referenced tweets sometimes contain only the id and the author.
     * This tries to load all other data from twitter. Unfortunately, in ~ 10% of the tweets, we get no result back.
     * To avoid marking tweets in the database, all empty tweets are loaded from the database and than batch processed.
     * Not optimal, but worked for ~ 2 mill tweets.
     */
    private void hydrateTweets() {

        log.info("start filling in details of tweets");
        List<Long> emptyTweets = database.getEmptyTweets();
        int cnt = 0;
        for (List<Long> ids : Lists.partition(emptyTweets, 1000)) {
            Set<Tweet> tweets = twitterService.fetchTweets(ids);
            cnt += tweets.size();
            database.persistTweets(tweets);
            resolveAndPersistUrlInTweets(tweets);
        }
        log.info("retrieved and stored {} tweets", cnt);
    }

    private void fetchAndUpdateTweetsOf(User user) {

        if (!user.tweetsNeedRescan() || user.isProtected()) {
            return;
        }

        try {
            log.debug("start refreshing tweets of User {}", user.getScreenName());
            Set<Tweet> tweetsToPersist = new HashSet<>();
            Set<Tweet> tweets = twitterService.fetchTimeline(user, database.getMaxTweetIdForUser(user));
            tweetsToPersist.addAll(fetchMissingInReplyTweets(tweets));
            tweetsToPersist.addAll(tweets);
            log.debug("tweets found: {}", tweetsToPersist.size());
            database.persistTweets(tweetsToPersist);
            resolveAndPersistUrlInTweets(tweetsToPersist);
        } catch (UserNotReadableException e) {
            user.setProtected(true);
            user.setTweetsLastScanned(LocalDateTime.now());
        }
        database.persistUsers(Collections.singleton(user));
    }

    private User fetchOrUpdateFriendsAndFollowersOf(User user) {

        if (user.ffNeedRescan() && !user.isProtected()) {
            try {
                log.info("start refreshing friends and followers of {}", user.getScreenName());
                user = twitterService.fillFriendsAndFollowers(user);
            } catch (UserNotReadableException e) {
                user.setProtected(true);
            }
            database.persistUsers(Collections.singleton(user));
        } else {
            log.debug("User {} is fresh, skipping", user.getScreenName());
            user.setFriends(database.loadFriends(user));
            user.setFollowers(database.loadFollowers(user));
        }
        return user;
    }

    private User refreshUser(User user) {

        if (!user.needRescan() || user.isProtected()) {
            return user;
        }

        try {
            log.debug("refreshing user {}", user.getScreenName());
            user = twitterService.lookupUser(user.getId());
            database.persistUsers(Collections.singleton(user));
        } catch (UserNotReadableException e) {
            user.setProtected(true);
        }
        return user;
    }

    /**
     * Updates Users from the given user in recursion.
     * On the first pass, only loads the tweets. If not on the first pass and
     * if maxDepth has not been reached, also loads friends and followers of the user
     * and goes deeper
     * @param user user to start from expanding from
     * @param currentDepth the current depth
     */
    private void followUser(User user, int currentDepth) {

        log.info("following user {} - {} - in level {}", user.getScreenName(), user.getId(), currentDepth);

        if (treeScanned.contains(user.getId())) {
            log.info("users {} tree already seen, skipping", user.getScreenName());
            return;
        }

        user = database.getUser(user).orElseThrow();
        if (user.isProtected()) {
            log.info("user {} is protected, skipping", user.getScreenName());
            return;
        }

        refreshUser(user);
        fetchAndUpdateTweetsOf(user);

        if (currentDepth <= maxDepth) {
            treeScanned.add(user.getId());
            user = fetchOrUpdateFriendsAndFollowersOf(user);
            Set<User> collect = Stream.concat(user.getFollowers().stream(), user.getFriends()
                    .stream())
                    .collect(Collectors.toUnmodifiableSet());
            Queue<User> work = new ArrayDeque<>(collect);
            while (!work.isEmpty()) {
                followUser(work.poll(), currentDepth + 1);
                log.debug("still processing {} on depth {}, {} f&f remaining",
                        user.getScreenName(), currentDepth, work.size());
            }
        }
    }

    private void queryTopHashtagForUser(User user) {
        Set<Hashtag> topHashtags = database.getTopHashtagsFor(user);
        topHashtags.forEach(this::queryForHashtag);
    }

    private void queryForHashtag(Hashtag hashtag) {

        log.info("start querying for Hashtag {}", hashtag.getHashtag());
        queryFor(new Query(Collections.singleton(hashtag.getHashtag())));
        hashtag.setLastScanned(LocalDateTime.now());
        database.persistHashtag(hashtag);
        updateUsersOfHashtag(hashtag);
    }

    private void updateUsersOfHashtag(Hashtag hashtag) {
       Set<User> users = database.getUsersForHashtag(hashtag);

       maxDepth = 0;

       users.forEach(user -> followUser(user,0));
    }

    private void queryFor(Query query) {

        log.info("start querying for '{}'", query.getQueryString());
        Set<Tweet> tweetsToPersist = new HashSet<>();
        Set<Tweet> tweets = twitterService.search(query);
        tweetsToPersist.addAll(fetchMissingInReplyTweets(tweets));
        tweetsToPersist.addAll(tweets);

        log.info("tweets found: {}", tweetsToPersist.size());
        database.persistTweets(tweetsToPersist);
        resolveAndPersistUrlInTweets(tweetsToPersist);
    }

    private void resolveAndPersistUrlInTweets(Collection<Tweet> tweets) {

        Set<String> urls = tweets.stream()
                .filter(t -> ! t.getUrls().isEmpty())
                .map(Tweet::getUrls).flatMap(Collection::stream)
                .collect(Collectors.toSet());


        Map<String,ResolveResult> result = urlResolver.resolve(urls);

        database.persistLinks(result);


    }

    private Set<Tweet> fetchMissingInReplyTweets(Set<Tweet> tweets) {
        Set<Long> locallyKnownTweetIds = tweets.stream().map(Tweet::getId).collect(Collectors.toUnmodifiableSet());

        Set<Long> locallyMissingIds = tweets.stream()
                .filter(tweet -> tweet.getInReplyToTweetId() != null)
                .filter(tweet -> ! locallyKnownTweetIds.contains(tweet.getId()))
                .map(Tweet::getId)
                .collect(Collectors.toUnmodifiableSet());

        Set<Long> missingTweetsById = database.findMissingTweetsById(locallyMissingIds);

        Set<Tweet> fetchedTweeds = twitterService.fetchTweets(missingTweetsById);
        if((missingTweetsById.size() - fetchedTweeds.size()) != 0) {
            log.info("we could not fetch / reference {} tweets from replies ",
                    missingTweetsById.size() - fetchedTweeds.size());
        }
        return fetchedTweeds;
    }
}
