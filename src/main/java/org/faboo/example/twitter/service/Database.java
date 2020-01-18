package org.faboo.example.twitter.service;

import org.faboo.example.twitter.data.Hashtag;
import org.faboo.example.twitter.data.Tweet;
import org.faboo.example.twitter.data.User;
import org.faboo.example.twitter.util.ResolveResult;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final Driver driver;

    public Database(Driver driver) {
        this.driver = driver;
    }

    void persistTweets(Collection<Tweet> tweets) {
        log.debug("persisting {} tweets", tweets.size());

        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                for (Tweet tweet : tweets) {
                    tx.run(" merge (t:Tweet {id:$tweet_id}) " +
                           "     on create set t = $tweet_props, t.id = $tweet_id " +
                           "     on match set t += $tweet_props " +
                           " merge (u:User {id:$author_id}) " +
                           "     on create set u = $author_props, u.id = $author_id " +
                           "     on match set u += $author_props " +
                           " MERGE (u)-[:POSTS]->(t) " +
                           " foreach(tag in $hashtags | " +
                           "     merge (h:Hashtag {name:tag}) " +
                           "     merge (h)-[:TAGS]->(t)" +
                           "        ) " +
                           " foreach(mention in $mentions | " +
                           "     merge (m:User {id:mention.id}) " +
                           "        on create set m.screenName = mention.screenName, m.id = mention.id " +
                           "     merge (t)-[:MENTIONED]->(m)" +
                                    " ) " +
                           " foreach( url in $urls | " +
                           "     merge (l:Link {url:url}) " +
                           "     merge (l)<-[:CONTAINS]-(t) " +
                           " ) ",
                            parameters(
                                    "tweet_id", tweet.getId(),
                                    "tweet_props", tweet.getProps(),
                                    "author_id", tweet.getUser().getId(),
                                    "author_props", tweet.getUser().getProps(),
                                    "hashtags", tweet.getHashtagsTags(),
                                    "mentions", tweet.getMentionedUsers(),
                                    "urls", tweet.getUrls())).consume();

                    if (tweet.getRetweetedTweet() != null) {
                        tx.run(" merge (t:Tweet {id:$t_id}) " +
                               " merge (r:Tweet {id:$r_id}) " +
                               "     on create set r = $r_props, r.id = $r_id " +
                               " merge (t)-[:RETWEETS]->(r) " +
                               " merge (u:User {id:$ru_id}) " +
                               "     on create set u = $ru_props, u.id = $ru_id " +
                               "     on match set u += $ru_props " +
                               " MERGE (u)-[:POSTS]->(r) ",
                                parameters(
                                        "t_id", tweet.getId(),
                                        "r_id", tweet.getRetweetedTweet().getId(),
                                        "r_props", tweet.getRetweetedTweet().getProps(),
                                        "ru_id", tweet.getRetweetedTweet().getUser().getId(),
                                        "ru_props", tweet.getRetweetedTweet().getUser().getProps()
                                        )).consume();
                    }

                    if (tweet.getInReplyToTweetId() != null) {
                        tx.run(" merge (t:Tweet {id:$t_id}) " +
                                        " merge (r:Tweet {id:$r_id}) " +
                                        " merge (t)-[:REPLY_TO]->(r) " +
                                        " merge (u:User {id:$ru_id}) " +
                                        "     on create set u.id = $ru_id, u.screenName = $ru_screenName " +
                                        " MERGE (u)-[:POSTS]->(r) ",
                                parameters(
                                        "t_id", tweet.getId(),
                                        "r_id", tweet.getInReplyToTweetId(),
                                        "ru_id", tweet.getInReplyToUserId(),
                                        "ru_screenName", tweet.getInReplyToUserScreenName()
                                )).consume();
                    }

                    if (tweet.getQuotedTweet() != null) {
                        tx.run(" merge (t:Tweet {id:$t_id}) " +
                                        " merge (q:Tweet {id:$q_id}) " +
                                        "     on create set q = $q_props, q.id = $q_id " +
                                        " merge (t)-[:QUOTES]->(q) " +
                                        " merge (u:User {id:$qu_id}) " +
                                        "     on create set u = $qu_props, u.id = $qu_id " +
                                        "     on match set u += $qu_props " +
                                        " MERGE (u)-[:POSTS]->(q) ",
                                parameters(
                                        "t_id", tweet.getId(),
                                        "q_id", tweet.getQuotedTweet().getId(),
                                        "q_props", tweet.getQuotedTweet().getProps(),
                                        "qu_id", tweet.getQuotedTweet().getUser().getId(),
                                        "qu_props", tweet.getQuotedTweet().getUser().getProps()
                                )).consume();
                    }
                }
                tx.success();
                return null;
            });
        }
    }


    void persistUsers(Collection<User> users) {
        log.debug("persisting {} users", users.size());
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                for (User user : users) {
                    tx.run("merge (u:User {id:$user_id}) " +
                                    "on create set u = $user_props, u.id = $user_id " +
                                    "on match set u += $user_props " +
                           " foreach( friend in $friends | " +
                           "     merge (f:User {id:friend.id}) " +
                           "         on create set f.id = friend.id, f.screenName = friend.screenName " +
                           "     merge (u)-[:FOLLOWS]->(f) " +
                           " ) " +
                           " foreach( follower in $followers | " +
                           "     merge (f:User {id:follower.id}) " +
                           "         on create set f.id = follower.id, f.screenName = follower.screenName " +
                           "     merge (f)-[:FOLLOWS]->(u) " +
                           " )",
                            parameters("user_id", user.getId(),
                                    "user_props", user.getProps(),
                                    "friends", user.getFriendsAsMap(),
                                    "followers", user.getFollowersAsMap())
                            ).consume();
                }
                tx.success();
                return null;
            });
        }
    }

    void persistHashtag(Hashtag hashtag) {
        log.debug("persisting Hashtag {}", hashtag);
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run(" merge (t:Hashtag {name:$name}) " +
                       "     on match set t.lastScanned = $lastScanned, " +
                       "         t.lastTweetSeen = $lastTweetSeen",
                        parameters(
                                "name", hashtag.getName(),
                                "lastTweetSeen", hashtag.getLastTweetSeen(),
                                "lastScanned", hashtag.getLastScanned())
                                ).consume();
                tx.success();
                return null;
            });
        }
    }

    void persistLinks(Map<String, ResolveResult> resolvedLinks) {

        List<Map<String,Object>> goodLinks = resolvedLinks.entrySet().stream()
                .filter(entry -> !entry.getValue().isError())
                .map(e -> {
                    Map<String,Object> map = new HashMap<>();
                    map.put("link", e.getKey());
                    map.put("url", e.getValue().getUrl());
                    map.put("site", e.getValue().getHostName());
                    return map;
                })
                .collect(Collectors.toList());

        List<Map<String,Object>> errorLinks = resolvedLinks.entrySet().stream()
                .filter(e -> e.getValue().isError())
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("link", e.getKey());
                    map.put("errorCode", e.getValue().getError().getStatus());
                    map.put("errorMessage", e.getValue().getError().getMessage());
                    return map;
                })
                .collect(Collectors.toList());

        log.debug("persisting {} good Links and {} error links", goodLinks.size(), errorLinks.size());

        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                        tx.run("foreach( link in $links | " +
                                        " merge (l:Link {url:link.link}) " +
                                        " merge (u:Url {url:link.url}) " +
                                        " merge (s:Site {name:link.site}) " +
                                        " merge (l)-[:LINKS_TO]->(u)-[:PART_OF]->(s) )",
                                parameters("links", goodLinks)
                              ).consume();
                tx.success();
                return null;
            });
        }
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run("foreach( link in $links | " +
                                " merge (l:Link {url:link.link}) " +
                                "   on create set l.errorCode = link.errorCode," +
                                "    l.errorMessage = link.errorMessage )",
                        parameters("links", errorLinks)
                ).consume();
                tx.success();
                return null;
            });
        }
    }

    Set<Long> findMissingTweetsById(Set<Long> toCheck) {

        if (toCheck.size() == 0) {
            return Collections.emptySet();
        }

        log.info("checking {} tweets for existance in database", toCheck.size());
        List<Long> ids = new ArrayList<>(toCheck);
        try (Session session= driver.session()) {

            return session.readTransaction(tx -> tx.run(
                    "match (tweet:Tweet) where not(id in $ids)) return tweet.id ",
                    parameters("ids", ids)
            ).stream()
                    .map(record -> record.get("id"))
                    .map(Value::asLong)
                    .collect(Collectors.toUnmodifiableSet()));
        }
    }

    Optional<String> getNextHashtagToScan(String startTag, int depth) {
        try (Session session= driver.session()) {
            // cypher does not permit parameter in variable depth path. We could also use apoc for this,
            // but right now, this works
            String statement = String.format(
                    "match p=(s:Hashtag {name:$startTag})-[:TAGS]->(:Tweet)<-[*1..%s]-(h:Hashtag) " +
                    "    where not(exists(h.lastScanned)) " +
                    "    with distinct h.name as hashtag, size((h)-[:TAGS]->()) as weight, length(p) as dist " +
                    "    return hashtag, dist, weight order by dist,weight desc limit 1", depth);
            return session.readTransaction(tx -> tx.run(statement, parameters("startTag", startTag)
            ).stream()
                    .map(record -> record.get("hashtag").asString())
                    .findFirst());
        }
    }

    Optional<User> getUser(User user) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (u:User) where u.id = $user_id" +
                    " return u",
                    parameters("user_id", user.getId())).stream()
                    .map(rec -> new User(rec.get("u").asMap()))
                    .findFirst());
        }
    }

    Optional<User> getUserByScreenName(String screenName) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (u:User) where u.screenName = $screenName" +
                    " return u",
                    parameters("screenName", screenName)).stream()
                    .map(rec -> new User(rec.get("u").asMap()))
                    .findFirst());
        }
    }

    Long getMaxTweetIdForUser(User user) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (u:User)-[:POSTS]-(t:Tweet) where u.id = $user_id" +
                    " return max(t.id) as max_id",
                    parameters("user_id", user.getId())).stream()
                    .map(rec -> rec.get("max_id").asLong(-1))
                    .findFirst().orElse(-1L));
        }
    }

    Set<User> loadFriends(User user) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (u:User)-[:FOLLOWS]->(friend:User) where u.id = $user_id" +
                    " return friend",
                    parameters("user_id", user.getId())).stream()
                    .map(rec -> new User(rec.get("friend").asMap())).collect(Collectors.toUnmodifiableSet()));
        }
    }

    Set<User> loadFollowers(User user) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (u:User)<-[:FOLLOWS]-(follower:User) where u.id = $user_id" +
                            " return follower",
                    parameters("user_id", user.getId())).stream()
                    .map(rec -> new User(rec.get("follower").asMap())).collect(Collectors.toUnmodifiableSet()));
        }
    }

    Set<Hashtag> getTopHashtagsFor(User user) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (n:User)-[:POSTS]->(:Tweet)<-[:TAGS]-(h:Hashtag)-[tags:TAGS]->(:Tweet) " +
                    "    where n.id = $user_id " +
                    "    with h.name as hashtag, count(tags) as weight order by weight desc limit 10 " +
                    "    return hashtag",
                    parameters("user_id", user.getId())
            ).stream()
                    .map(rec -> rec.get("hashtag").asString())
                    .map(Hashtag::new)
                    .collect(Collectors.toUnmodifiableSet()));
        }
    }

    List<Long> getEmptyTweets() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    "match (t:Tweet) where not exists(t.text) return t.id as id"
            )).stream()
                    .map(rec -> rec.get("id").asLong())
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    public Set<User> getUsersForHashtag(Hashtag hashtag) {
        try (Session session= driver.session()) {
            return session.readTransaction(tx -> tx.run(
                    " match (u:User)-[:POSTS]->()<-[:TAGS]-(t:Hashtag {name:$hashtag}) return u",
                    parameters("hashtag", hashtag.getName())).stream()
                    .map(rec -> new User(rec.get("u").asMap())).collect(Collectors.toUnmodifiableSet()));
        }
    }
}
