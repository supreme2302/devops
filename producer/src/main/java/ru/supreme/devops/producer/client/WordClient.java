package ru.supreme.devops.producer.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Component
public class WordClient {
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public WordClient(@Value("${client.word.name}") String clientName,
                      @Value("${client.word.readTimeout}") Long readTimeout,
                      @Value("${client.word.connectTimeout}") Integer connectTimeout,
                      CircuitBreakerRegistry registry,
                      WebClient.Builder webClientBuilder) {
        this.circuitBreaker = registry.circuitBreaker(clientName);
        this.webClient = createWebClientBuilderWithConnectAndReadTimeOuts(webClientBuilder, connectTimeout, readTimeout)
                .baseUrl("http://" + clientName)
                .build();
    }

    public Mono<String> getWord() {
        return webClient
                .get()
                .uri("/word")
                .retrieve()
                .bodyToMono(String.class)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(throwable -> Mono.just("Fallback (Producer)"));
    }

    //todo: Можно вынести в common либу
    private WebClient.Builder createWebClientBuilderWithConnectAndReadTimeOuts(WebClient.Builder builder, int connectTimeout, long readTimeout) {
        HttpClient httpClient = HttpClient.create()
                .tcpConfiguration(tcpClient -> {
                    tcpClient = tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
                    tcpClient = tcpClient.doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS)));
                    return tcpClient;
                });
        ClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        return builder
                .clientConnector(connector);
    }
}
