package org.faboo.example.twitter;

import org.faboo.example.twitter.service.CrawlService;
import org.faboo.example.twitter.service.Database;
import org.faboo.example.twitter.service.ForkJoinResolver;
import org.faboo.example.twitter.service.twitter.TwitterService;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@SpringBootApplication
@PropertySource({"classpath:twitter.properties"})
public class TwitterApplication {

    public static void main(String[] args) {
       SpringApplication.run(TwitterApplication.class, args);
    }


    @ConfigurationProperties(prefix="twitter")
    @Bean(initMethod = "init")
    public TwitterService twitterService() {
        return new TwitterService();
    }

    @Bean
    @Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public CrawlService crawlService(Driver driver) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return new CrawlService(twitterService(), database(driver), forkJoinResolver());
    }

    @Bean
    public Database database(Driver driver) {
        return new Database(driver);
    }

    @Bean
    public ForkJoinResolver forkJoinResolver() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return new ForkJoinResolver();
    }
}
