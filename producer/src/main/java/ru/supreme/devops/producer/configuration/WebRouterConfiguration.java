package ru.supreme.devops.producer.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import ru.supreme.devops.producer.handler.WordHandler;

@Configuration
@RequiredArgsConstructor
public class WebRouterConfiguration {
    private final WordHandler client;

    @Bean
    public RouterFunction<?> routerFunction() {
        return RouterFunctions.route()
                .GET("/word", client::getWord)
                .build();
    }
}
