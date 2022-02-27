package sk.tiku.core.networking;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpRequestFactory;
import sk.tiku.core.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper around {@link HttpClient} library to simplify most common tasks
 */
public class TikuHttpClient {
    HttpClient client = HttpClients.createDefault();

    /**
     * HTTP GET METHOD
     *
     * @param url Url to fetch
     */
    public String get(String url) {
        try {
            Logger.getInstance().debug(String.format("HTTP GET %s", url));
            ClassicHttpRequest request = DefaultClassicHttpRequestFactory.INSTANCE.newHttpRequest("GET", url);
            ClassicHttpResponse response = client.execute(HttpHost.create(url), request);
            int responseCode = response.getCode();
            Logger.getInstance().debug(String.format("HTTP GET %s finished: %d", url, responseCode));
            try (InputStream is = response.getEntity().getContent()) {
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } catch (IOException | URISyntaxException e) {
            Logger.getInstance().error(String.format("Could not execute HTTP GET: %s", url), e);
            throw new RuntimeException(e);
        }
    }
}
