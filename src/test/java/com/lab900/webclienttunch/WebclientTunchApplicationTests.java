package com.lab900.webclienttunch;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
class WebclientTunchApplicationTests {
  WireMockServer wireMockServer;
  WebTestClient testClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(options().dynamicPort());
    wireMockServer.stubFor(get(urlMatching("/")).willReturn(WireMock.ok()));
    wireMockServer.stubFor(post(urlMatching("/")).willReturn(WireMock.ok()));
    wireMockServer.stubFor(post(urlMatching("/authenticated")).willReturn(WireMock.unauthorized()));
    wireMockServer.start();

    testClient = WebTestClient.bindToServer().baseUrl(wireMockServer.baseUrl()).build();
  }

  @Test
  void testGet() {
    testClient.get().uri("/").exchange().expectStatus().isOk();
    wireMockServer.verify(1, getRequestedFor(urlMatching("/")));
  }

  @Test
  void testPost() {
    testClient
        .post()
        .uri("/")
        .bodyValue(Map.of("content", Map.of("key1", "value1", "key2", "value2")))
        .exchange()
        .expectStatus()
        .isOk();
    wireMockServer.verify(
        1,
        postRequestedFor(urlMatching("/"))
            .withRequestBody(
                equalToJson(
                    """
                      {
                        "content": {
                          "key1": "value1",
                          "key2": "value2"
                        }
                      }
            """)));
  }

  @Test
  void testPostAuthenticated() {
    testClient.post().uri("/authenticated").exchange().expectStatus().isUnauthorized();
    wireMockServer.verify(1, postRequestedFor(urlMatching("/authenticated")));
  }
}
