package ru.supreme.devops.consumer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.supreme.devops.consumer.client.ProducerClient;

@RestController
@RequiredArgsConstructor
public class GreetingController {
    private final ProducerClient client;

    @GetMapping("/greeting/{name}")
    public String greeting(@PathVariable String name) {
        String w = client.getWord();
        return String.format("%s, %s", w, name);
    }
}
