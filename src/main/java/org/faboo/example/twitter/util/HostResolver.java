package org.faboo.example.twitter.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HostResolver {


    private static final List<Pattern> prefixesToDelete = Arrays.asList(
            Pattern.compile("^(m\\.).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(www\\.).*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(mobile\\.).*", Pattern.CASE_INSENSITIVE));

    static String getCleanedHost(String urlString) throws MalformedURLException {

        URL url = new URL(urlString);

        String host = url.getHost();

        for (Pattern pattern : prefixesToDelete) {
            Matcher matcher = pattern.matcher(host);
            if(matcher.find()) {
                host = host.subSequence(matcher.group(1).length(), host.length()).toString();
            }
        }

        return host;
    }

    static String buildAbsoluteUrl(String urlString, String path) throws MalformedURLException {


            if (path.toLowerCase().startsWith("http")) {
                return path;
            }
            URL url = new URL(urlString);
            return url.getProtocol() + "://" + url.getHost() + (url.getPort() == -1 ? "" : ":"+ url.getPort()) + path;
        }

}
