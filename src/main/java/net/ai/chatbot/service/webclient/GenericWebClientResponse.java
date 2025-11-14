package net.ai.chatbot.service.webclient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

/**
 * Simple wrapper around a WebClient response providing the decoded body,
 * status code, and headers in a synchronous context.
 *
 * @param <R> the response body type
 */
public class GenericWebClientResponse<R> {

    private final R body;
    private final HttpStatusCode statusCode;
    private final HttpHeaders headers;

    public GenericWebClientResponse(R body, HttpStatusCode statusCode, HttpHeaders headers) {
        this.body = body;
        this.statusCode = statusCode;
        this.headers = headers;
    }

    public R getBody() {
        return body;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public boolean is2xxSuccessful() {
        return statusCode != null && statusCode.is2xxSuccessful();
    }
}

