package org.faboo.example.twitter.service.twitter;

import java.util.Collection;

public class Query {

    private final twitter4j.Query query;

    public Query(Collection<String> words) {

        query = new twitter4j.Query(String.format("(%s)",String.join(" OR ", words)));
        query.setResultType(twitter4j.Query.RECENT);
        query.setCount(100);
    }

    twitter4j.Query getQuery() {
        return query;
    }

    public String getQueryString() {
        return query.getQuery();
    }

    public void setQuery(String query) {
        this.query.setQuery(query);
    }

    public long getMaxId() {
        return query.getMaxId();
    }

    public void setMaxId(long maxId) {
        query.setMaxId(maxId);
    }

    public Query maxId(long maxId) {
        query.maxId(maxId);
        return this;
    }

    public int getCount() {
        return query.getCount();
    }

    public twitter4j.Query count(int count) {
        return query.count(count);
    }

    public String getSince() {
        return query.getSince();
    }

    public void setSince(String since) {
        query.setSince(since);
    }

    public Query since(String since) {
        query.since(since);
        return this;
    }

    public long getSinceId() {
        return query.getSinceId();
    }

    public void setSinceId(long sinceId) {
        query.setSinceId(sinceId);
    }

    public Query sinceId(long sinceId) {
        query.sinceId(sinceId);
        return this;
    }

    public String getUntil() {
        return query.getUntil();
    }

    public void setUntil(String until) {
        query.setUntil(until);
    }

    public Query until(String until) {
        query.until(until);
        return this;
    }

}
