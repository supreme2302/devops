package ru.supreme.devops.consumer.client;

import org.springframework.stereotype.Component;

@Component
public class FallbackProducerClient implements ProducerClient {

    @Override
    public String getWord() {
        return "Fallback";
    }
}
