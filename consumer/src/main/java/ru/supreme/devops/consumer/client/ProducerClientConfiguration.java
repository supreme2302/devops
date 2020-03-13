package ru.supreme.devops.consumer.client;


import feign.Feign;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
public class ProducerClientConfiguration {
    private final CircuitBreakerRegistry registry;
    private final FallbackProducerClient fallbackProducerClient;

    @Value("${client.producer.name}")
    private String clientName;

    @Bean
    @Scope("prototype")
    public Feign.Builder feignBuilder() {
        CircuitBreaker circuitBreaker = registry.circuitBreaker(clientName);
        FeignDecorators decorators = FeignDecorators.builder()
                .withCircuitBreaker(circuitBreaker)
                .withFallback(fallbackProducerClient)
                .build();
        return Resilience4jFeign.builder(decorators);
    }
}
