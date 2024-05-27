package com.lab900.webclienttunch;

import lombok.extern.slf4j.Slf4j;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.shell.boot.TerminalCustomizer;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@SpringBootApplication
@ShellComponent
@Slf4j
public class WebclientTunchApplication {
  private static final String BASE_URL = "http://localhost:8100";
  private String cachedToken = "invalid_token";

  public static void main(String[] args) {
    SpringApplication.run(WebclientTunchApplication.class, args);
  }

  @ShellMethod(value = "Get the endpoint", key = "get")
  public void getEndpoint() {
    var client = WebClient.create(BASE_URL);
    Mono<?> response = client.get().uri("/").retrieve().bodyToMono(String.class);
    log.info(response.block().toString());
  }

  @ShellMethod(value = "Get the endpoint", key = "get-non-blocking")
  public void getEndpointNonBlocking() {
    var client = WebClient.create(BASE_URL);
    Mono<?> response = client.get().uri("/").retrieve().bodyToMono(String.class);
    response.subscribe(responseBody -> log.info(responseBody.toString()));
  }

  @ShellMethod(value = "Post to the endpoint", key = "post")
  public void postEndpoint() {
    var body = Map.of("content", Map.of("company", "Lab900", "age", 30));
    var client = WebClient.create(BASE_URL);
    Mono<?> response =
        client
            .post()
            .uri("/")
            .body(BodyInserters.fromValue(body))
            .retrieve()
            .bodyToMono(String.class);
    log.info(response.block().toString());
  }

  @ShellMethod(value = "Post to the endpoint with token and error handling", key = "post-token")
  public void postWithErrorHandling() {
    var client = getWebClientWithToken().build();

    client
        .post()
        .uri("/authenticated")
        .body(BodyInserters.fromValue(Map.of("company", "Lab900", "age", 30)))
        .retrieve()
        .onStatus(
            WebclientTunchApplication::isHandledClientError,
            clientResponse -> {
              log.info("Client error: {}", clientResponse.statusCode());
              log.info("Resetting cached Token");
              this.cachedToken = null;
              return clientResponse.createException();
            })
        .bodyToMono(String.class)
        .retryWhen(
            Retry.backoff(3, Duration.ofSeconds(2))
                .jitter(.5)
                .filter(
                    throwable ->
                        throwable instanceof WebClientResponseException responseException
                            && isHandledError(responseException.getStatusCode()))
                .onRetryExhaustedThrow(
                    (spec, signal) -> {
                      log.warn(
                          "Retries ({}) exhausted for request to {}", spec.maxAttempts, BASE_URL);
                      return signal.failure();
                    }))
        .blockOptional()
        .ifPresent(log::info);
  }

  private WebClient.Builder getWebClientWithToken() {
    return WebClient.builder()
        .baseUrl(BASE_URL)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .filter(
            ExchangeFilterFunction.ofRequestProcessor(
                request ->
                    (cachedToken != null
                            ? Mono.just(cachedToken)
                            : (WebClient.create(BASE_URL)
                                .get()
                                .uri("/token")
                                .retrieve()
                                .bodyToMono(String.class)))
                        .map(
                            token ->
                                ClientRequest.from(request)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .build())));
  }

  private static boolean isHandledError(HttpStatusCode status) {
    return isHandledClientError(status) || isHandledServerError(status);
  }

  private static boolean isHandledClientError(HttpStatusCode status) {
    return Stream.of(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED)
        .map(HttpStatus::value)
        .anyMatch(statusCode -> Objects.equals(status.value(), statusCode));
  }

  private static boolean isHandledServerError(HttpStatusCode status) {
    return Stream.of(
            HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)
        .map(HttpStatus::value)
        .anyMatch(statusCode -> Objects.equals(status.value(), statusCode));
  }

  @Bean
  public TerminalCustomizer terminalCustomizer() {
    return terminalBuilder -> terminalBuilder.system(true);
  }

  @Bean
  public PromptProvider myPromptProvider() {
    return () ->
        new AttributedString(
            "webclient-tunch:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
  }
}
