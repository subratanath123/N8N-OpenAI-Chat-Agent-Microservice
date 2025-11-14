package net.ai.chatbot.service.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Optional.empty;

/**
 * Generic WebClient utility for making HTTP requests
 */
@Slf4j
@Component
public class GenericWebClient {

    private final WebClient.Builder webClientBuilder;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public GenericWebClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public <T, R> R post(String url,
                         Supplier<T> requestPayload,
                         Class<R> responseType,
                         Map<String, String> headers) {
        try {
            log.info("POST request to URL: {}", url);

            WebClient webClient = buildWebClient(url, headers);

            T payload = requestPayload.get();

            WebClient.RequestBodySpec requestSpec = webClient.post().uri(url);

            R response;
            if (payload instanceof BodyInserter<?, ?> bodyInserter) {
                // Don't set content type - let the BodyInserter set it (form data vs multipart)
                @SuppressWarnings("unchecked")
                BodyInserter<Object, ? super ClientHttpRequest> inserter =
                        (BodyInserter<Object, ? super ClientHttpRequest>) bodyInserter;

                response = requestSpec
                        .body(inserter)
                        .retrieve()
                        .bodyToMono(responseType)
                        .timeout(DEFAULT_TIMEOUT)
                        .block();
            } else {
                requestSpec.contentType(MediaType.APPLICATION_JSON);
                response = requestSpec
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(responseType)
                        .timeout(DEFAULT_TIMEOUT)
                        .block();
            }

            log.info("POST request successful to: {}", url);
            return response;

        } catch (WebClientResponseException e) {
            log.error("WebClient POST request failed with status: {} for URL: {}",
                    e.getStatusCode(), url, e);
            throw new RuntimeException("POST request failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error during POST request to URL: {}", url, e);
            throw new RuntimeException("POST request error: " + e.getMessage(), e);
        }
    }

    public <T, R> GenericWebClientResponse<R> postWithResponse(String url,
                                                               Supplier<T> requestPayload,
                                                               Class<R> responseType,
                                                               Map<String, String> headers) {
        try {
            log.info("POST request with response details to URL: {}", url);

            WebClient webClient = buildWebClient(url, headers);

            T payload = requestPayload.get();

            WebClient.RequestBodySpec requestSpec = webClient.post().uri(url);

            Mono<GenericWebClientResponse<R>> responseMono;
            if (payload instanceof BodyInserter<?, ?> bodyInserter) {
                // Don't set content type - let the BodyInserter set it (form data vs multipart)
                @SuppressWarnings("unchecked")
                BodyInserter<Object, ? super ClientHttpRequest> inserter =
                        (BodyInserter<Object, ? super ClientHttpRequest>) bodyInserter;

                responseMono = exchangeWithResponse(requestSpec.body(inserter), responseType);

            } else {
                requestSpec.contentType(MediaType.APPLICATION_JSON);
                responseMono = exchangeWithResponse(requestSpec.bodyValue(payload), responseType);
            }

            GenericWebClientResponse<R> response = responseMono
                    .timeout(DEFAULT_TIMEOUT)
                    .block();

            log.info("POST request with response details successful to: {}", url);
            return response;

        } catch (WebClientResponseException e) {
            log.error("WebClient POST request failed with status: {} for URL: {}",
                    e.getStatusCode(), url, e);
            throw new RuntimeException("POST request failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error during POST request to URL: {}", url, e);
            throw new RuntimeException("POST request error: " + e.getMessage(), e);
        }
    }

    public <T, R> R post(String url, Supplier<T> requestPayload, Class<R> responseType) {
        return post(url, requestPayload, responseType, null);
    }

    public <T, R> Mono<R> postAsync(String url,
                                    Supplier<T> requestPayload,
                                    Class<R> responseType,
                                    Map<String, String> headers,
                                    Consumer<R> onSuccess,
                                    Consumer<Throwable> onFailure) {
        try {
            log.info("Async POST request to URL: {}", url);

            WebClient webClient = buildWebClient(url, headers);

            T payload = requestPayload.get();
            WebClient.RequestBodySpec requestSpec = webClient.post().uri(url);

            Mono<R> responseMono;
            if (payload instanceof BodyInserter<?, ?> bodyInserter) {
                // Don't set content type - let the BodyInserter set it (form data vs multipart)
                @SuppressWarnings("unchecked")
                BodyInserter<Object, ? super ClientHttpRequest> inserter =
                        (BodyInserter<Object, ? super ClientHttpRequest>) bodyInserter;

                responseMono = requestSpec
                        .body(inserter)
                        .retrieve()
                        .bodyToMono(responseType)
                        .timeout(DEFAULT_TIMEOUT);
            } else {
                requestSpec.contentType(MediaType.APPLICATION_JSON);
                responseMono = requestSpec
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(responseType)
                        .timeout(DEFAULT_TIMEOUT);
            }

            return responseMono
                    .doOnSuccess(response -> {
                        log.info("Async POST request successful to: {}", url);
                        onSuccess.accept(response);
                    })
                    .doOnError(e -> {
                        log.error("Async POST request failed to: {}", url, e);
                        onFailure.accept(e);
                    });

        } catch (Exception e) {
            log.error("Error during async POST request to URL: {}", url, e);
            return Mono.error(new RuntimeException("Async POST request error: " + e.getMessage(), e));
        }
    }

    public <T, R> Mono<R> postAsync(String url, Supplier<T> requestPayload, Class<R> responseType) {
        return postAsync(url, requestPayload, responseType, null, r -> {}, throwable -> {});
    }

    public <R> R get(String url,
                     Class<R> responseType,
                     Map<String, String> headers,
                     Map<String, String> queryParams) {
        try {
            log.info("GET request to URL: {}", url);

            WebClient webClient = buildWebClient(url, headers);

            WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(url);
                        if (queryParams != null && !queryParams.isEmpty()) {
                            queryParams.forEach(uriBuilder::queryParam);
                        }
                        return uriBuilder.build();
                    });

            R response = requestSpec
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .block();

            log.info("GET request successful to: {}", url);
            return response;

        } catch (WebClientResponseException e) {
            log.error("WebClient GET request failed with status: {} for URL: {}",
                    e.getStatusCode(), url, e);
            throw new RuntimeException("GET request failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error during GET request to URL: {}", url, e);
            throw new RuntimeException("GET request error: " + e.getMessage(), e);
        }
    }

    public <R> R get(String url, Class<R> responseType) {
        return get(url, responseType, null, null);
    }

    public <R> R get(String url, Class<R> responseType, Map<String, String> queryParams) {
        return get(url, responseType, null, queryParams);
    }

    public <R> Mono<R> getAsync(String url,
                                Class<R> responseType,
                                Map<String, String> headers,
                                Map<String, String> queryParams) {
        try {
            log.info("Async GET request to URL: {}", url);

            WebClient webClient = buildWebClient(url, headers);

            WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(url);
                        if (queryParams != null && !queryParams.isEmpty()) {
                            queryParams.forEach(uriBuilder::queryParam);
                        }
                        return uriBuilder.build();
                    });

            return requestSpec
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .doOnSuccess(response -> log.info("Async GET request successful to: {}", url))
                    .doOnError(e -> log.error("Async GET request failed to: {}", url, e));

        } catch (Exception e) {
            log.error("Error during async GET request to URL: {}", url, e);
            return Mono.error(new RuntimeException("Async GET request error: " + e.getMessage(), e));
        }
    }

    public <R> Mono<R> getAsync(String url, Class<R> responseType) {
        return getAsync(url, responseType, null, null);
    }

    private <R> Mono<GenericWebClientResponse<R>> exchangeWithResponse(WebClient.RequestHeadersSpec<?> requestSpec,
                                                                       Class<R> responseType) {
        return requestSpec
                .exchangeToMono(clientResponse -> {
                    HttpStatusCode statusCode = clientResponse.statusCode();
                    if (statusCode.isError()) {
                        return clientResponse.createException().flatMap(Mono::error);
                    }

                    return clientResponse.bodyToMono(responseType)
                            .map(Optional::of)
                            .defaultIfEmpty(empty())
                            .map(optionalBody -> new GenericWebClientResponse<>(
                                    optionalBody.orElse(null),
                                    statusCode,
                                    new HttpHeaders(clientResponse.headers().asHttpHeaders())
                            ));
                });
    }

    private WebClient buildWebClient(String baseUrl, Map<String, String> headers) {
        WebClient.Builder builder = webClientBuilder.clone();

        if (headers != null && !headers.isEmpty()) {
            headers.forEach((key, value) -> {
                if (value != null) {
                    builder.defaultHeader(key, value);
                }
            });
        }

        return builder.build();
    }

    public <T, R> R post(String url,
                         Supplier<T> requestPayload,
                         Class<R> responseType,
                         Map<String, String> headers,
                         Duration timeout) {
        try {
            log.info("POST request to URL: {} with timeout: {}", url, timeout);

            WebClient webClient = buildWebClient(url, headers);

            T payload = requestPayload.get();

            WebClient.RequestBodySpec requestSpec = webClient.post().uri(url);

            R response;
            if (payload instanceof BodyInserter<?, ?> bodyInserter) {
                // Don't set content type - let the BodyInserter set it (form data vs multipart)
                @SuppressWarnings("unchecked")
                BodyInserter<Object, ? super ClientHttpRequest> inserter =
                        (BodyInserter<Object, ? super ClientHttpRequest>) bodyInserter;
                response = requestSpec
                        .body(inserter)
                        .retrieve()
                        .bodyToMono(responseType)
                        .timeout(timeout)
                        .block();
            } else {
                requestSpec.contentType(MediaType.APPLICATION_JSON);
                response = requestSpec
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(responseType)
                        .timeout(timeout)
                        .block();
            }

            log.info("POST request successful to: {}", url);
            return response;

        } catch (WebClientResponseException e) {
            log.error("WebClient POST request failed with status: {} for URL: {}",
                    e.getStatusCode(), url, e);
            throw new RuntimeException("POST request failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error during POST request to URL: {}", url, e);
            throw new RuntimeException("POST request error: " + e.getMessage(), e);
        }
    }

    public <R> R get(String url,
                     Class<R> responseType,
                     Map<String, String> headers,
                     Map<String, String> queryParams,
                     Duration timeout) {
        try {
            log.info("GET request to URL: {} with timeout: {}", url, timeout);

            WebClient webClient = buildWebClient(url, headers);

            WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(url);
                        if (queryParams != null && !queryParams.isEmpty()) {
                            queryParams.forEach(uriBuilder::queryParam);
                        }
                        return uriBuilder.build();
                    });

            R response = requestSpec
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(timeout)
                    .block();

            log.info("GET request successful to: {}", url);
            return response;

        } catch (WebClientResponseException e) {
            log.error("WebClient GET request failed with status: {} for URL: {}",
                    e.getStatusCode(), url, e);
            throw new RuntimeException("GET request failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error during GET request to URL: {}", url, e);
            throw new RuntimeException("GET request error: " + e.getMessage(), e);
        }
    }
}