# Twitter Crawler for Neo4j

A little script to import twitter data into neo4j. Uses Twitter4j and can be configured
to use multiple twitter authentications for a speedup.

For the Neo4j schema, I followed the one given in [the OSCON Twitter Graph](https://neo4j.com/blog/oscon-twitter-graph/)

It allows crawling data from a given user or hashtag with a given max depth. Due to exponential growth,
setting the search depth to anything higher than 1 will probably take more than 2 days.. 

The access tokens for the Twitter API must be configured. See below on the format.
The script tries to use the available requests/buckets as efficient as possible. Loading 
friends and followers of a user is especially expensive and is therefor limited. If the loading of 
friends or followers will take more than an hour (this depends on the number of access tokens), 
than the loading will be skipped.

As the limiting factor is the Twitter API rate limit, I decided not to make the script multi-threaded. It allowed to 
keep things simple.

## Twitter access tokens
The crawler needs access tokes for the twitter API. These can be obtained from the [Twitter dev console](https.dev.twitter.com)
As the (free) Twitter API is very restricted in the number of calls per time bucket, you can register an arbitrary 
number of access tokens in a file ```twitter.properties``` that must reside on the classpath. 


```
twitter.access[0].name=identifier for logging
twitter.access[0].consumerKey=**
twitter.access[0].consumerSecret=**
twitter.access[0].accessToken=**
twitter.access[0].accessTokenSecret=**

twitter.access[1].name=identifier for logging
twitter.access[1].consumerKey=**
twitter.access[1].consumerSecret=**
twitter.access[1].accessToken=**
twitter.access[1].accessTokenSecret=**
```

## Calling the script
The behaviour can be controlled via command line arguments. They are evaluated/executed in the order given below. This allows to chain
actions and let the script run in the background.

### setting max depth
The default max depth to follow hashtags or users is 3. This can be changed via `--depth=n` with n any number.

### querying for a single hashtag
To query for a single hashtag, give the hashtag via the option `--hash=graphdatabase`. Would query for the hashtag `#neo4j` 
and store all object referenced in a Tweet (author, hashtags, mentioned users, links,..). Users will only contain the twitter id and screenName.

### following a user
Via `--follow-user=neo4j` the script will request the user with the given screen name and load tweets of tht users. It will than 
descend along the friends and followers of that user and load their tweets and followers until it reaches the given max depth.

### parse the top hashtags of a user
With `--follow-user-hashtags=neo4j` the script will determine the 10 most used hashtags of that user and loads all tweets with that 
hashtag. This of course needs some data in the database to bee meaningful. Can be combined with `--follow-user` to load the hashtags of that 
user afterwards.

### follow a list of hashtags
To request (and store) depended hashtags (often used together), provide a starting hashtag via `--follow-hashtag=aHashTag`. This will 
retrieve and store hashtags up to the provided `--depth`.

### hydrate tweets
Referenced tweets are sometimes returned with only the ID and the author. By providing ```--hydrate-tweets```
as option, the program tries to load additional data from twitter. In about 10% of tweets, this fails. For whatever reason.
