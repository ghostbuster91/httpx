package org.mvnsearch.http.protocol;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mvnsearch.http.logging.HttpxErrorCodeLogger;
import org.mvnsearch.http.logging.HttpxErrorCodeLoggerFactory;
import org.mvnsearch.http.model.HttpCookie;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class HttpBaseExecutor implements BaseExecutor {
    private static final HttpxErrorCodeLogger log = HttpxErrorCodeLoggerFactory.getLogger(HttpBaseExecutor.class);

    protected HttpClient httpClient() {
        return HttpClient.create().secure(sslContextSpec -> {
            try {
                sslContextSpec.sslContext(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build());
            } catch (Exception ignore) {

            }
        });
    }

    protected List<HttpCookie> cookies(String domain) {
        try {
            final Path cookieFile = Path.of(".idea/httpRequests/http-client.cookies");
            if (cookieFile.toFile().exists()) {
                final List<String> lines = Files.readAllLines(cookieFile);
                if (lines.size() > 1) {
                    List<HttpCookie> cookies = new ArrayList<>();
                    final long now = System.currentTimeMillis();
                    for (String line : lines.subList(1, lines.size())) {
                        final HttpCookie cookie = HttpCookie.valueOf(line);
                        if (cookie.getDomain().equalsIgnoreCase(domain) && cookie.getExpired().getTime() > now) {
                            cookies.add(cookie);
                        }
                    }
                    return cookies;
                }
            }
        } catch (Exception e) {
            log.error("HTX-100-600", e);
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public List<byte[]> request(HttpClient.ResponseReceiver<?> responseReceiver, URI requestUri) {
        return responseReceiver
                .uri(requestUri)
                .response((response, byteBufFlux) -> {
                    final HttpResponseStatus httpStatus = response.status();
                    if (httpStatus == HttpResponseStatus.OK) {
                        System.out.println(colorOutput("green", "Status: " + httpStatus));
                    } else {
                        System.out.println(colorOutput("bold,red", "Status: " + httpStatus));
                    }
                    final HttpHeaders responseHeaders = response.responseHeaders();
                    //color header
                    responseHeaders.forEach(header -> System.out.println(colorOutput("green", header.getKey()) + ": " + header.getValue()));
                    System.out.println();
                    String contentType = responseHeaders.get("Content-Type");
                    return byteBufFlux.asByteArray().doOnNext(bytes -> {
                        if (contentType != null && isPrintable(contentType)) {
                            if (contentType.contains("json")) {
                                final String body = prettyJsonFormat(new String(bytes, StandardCharsets.UTF_8));
                                System.out.print(body);
                            } else {
                                System.out.print(new String(bytes));
                            }
                        }
                    });
                }).buffer().blockLast();
    }
}
