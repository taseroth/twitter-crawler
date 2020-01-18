package org.faboo.example.twitter.util;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;

class HostResolverTest {

    @Test
    void mustRemoveWWW() throws MalformedURLException {
        HostResolver resolver = new HostResolver();

        String cleanedHost = resolver.getCleanedHost("http://www.google.com/faa/boo");

        assertThat(cleanedHost).isEqualTo("google.com");
    }

    @Test
    void mustRemoveMobileShort() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String cleanedHost = resolver.getCleanedHost("http://m.google.com/faa/boo");

        assertThat(cleanedHost).isEqualTo("google.com");
    }

    @Test
    void mustRemoveMobileLong() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String cleanedHost = resolver.getCleanedHost("http://mobile.google.com/faa/boo");

        assertThat(cleanedHost).isEqualTo("google.com");
    }

    @Test
    void mustOnlyWorkAtBeginningOfName() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String cleanedHost = resolver.getCleanedHost("http://instagram.com/faa/boo");

        assertThat(cleanedHost).isEqualTo("instagram.com");
    }

    @Test
    void buildAbsoluteUrlAlreadyComplete() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String url = resolver.buildAbsoluteUrl("http://google.com/fourbar?122", "http://google.com/fourbar?122");
        assertThat(url).isEqualTo("http://google.com/fourbar?122");
    }

    @Test
    void buildAbsoluteUrlWithoutPortHttp() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String url = resolver.buildAbsoluteUrl("http://google.com/fourbar?122", "/mypath");
        assertThat(url).isEqualTo("http://google.com/mypath");
    }

    @Test
    void buildAbsoluteUrlWithoutPortHttps() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String url = resolver.buildAbsoluteUrl("https://google.com/fourbar?122", "/mypath");
        assertThat(url).isEqualTo("https://google.com/mypath");
    }

    @Test
    void buildAbsoluteUrlWithPortHttp() throws MalformedURLException {

        HostResolver resolver = new HostResolver();

        String url = resolver.buildAbsoluteUrl("http://google.com:8080/fourbar?122", "/mypath");
        assertThat(url).isEqualTo("http://google.com:8080/mypath");
    }

}