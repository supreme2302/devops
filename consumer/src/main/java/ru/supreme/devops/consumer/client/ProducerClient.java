package ru.supreme.devops.consumer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "${client.producer.name}", configuration = ProducerClientConfiguration.class)
public interface ProducerClient {

    @RequestMapping(value = "/word", method = RequestMethod.GET)
    String getWord();
}
