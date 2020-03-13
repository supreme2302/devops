package ru.supreme.devops.producer.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import ru.supreme.devops.producer.client.WordClient;

@Component
@RequiredArgsConstructor
public class WordHandler {
    private final WordClient client;

    public Mono<ServerResponse> getWord(ServerRequest request) {
        return ServerResponse
                .ok()
                .body(client.getWord(), String.class);
    }
}
