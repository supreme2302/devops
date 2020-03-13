# Пример Spring Boot приложений для деплоя в OpenShift

## Non reactive
### Http клиент
Для межсервисных запросов в приоритете использование OpenFeign Client

Зависимости:
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
```

Для активации клиентов нужна аннотация @EnableFeignClients на файле конфигурации.

Пример клиента:
```java
@FeignClient(name = "${client.producer.name}")
public interface ProducerClient {

    @RequestMapping(value = "/word", method = RequestMethod.GET)
    String getWord();
}
```

По данному интерфейсу spring сам создаст bean который можно заинжектить в свои классы.

Для конфигурации клиента в application.yaml файле можно сделать так (полный список параметров см. в [документации](https://cloud.spring.io/spring-cloud-openfeign/reference/html/)):
```yaml
feign:
  client:
    config:
      producer-service:
        connectTimeout: 1000
        readTimeout: 1000
```
### Circuit Breaker
Каждый клиент необходимо обеспечить поддержкой Circuit Breaker.
Будем использовать [Resilience4j](https://resilience4j.readme.io/docs/circuitbreaker) т.к. Hystrix больше не будет поддерживаться Netflix

Зависимости:
```groovy
set('resilience4jVersion', '1.1.0')

implementation "io.github.resilience4j:resilience4j-spring-boot2:${resilience4jVersion}"
implementation "io.github.resilience4j:resilience4j-feign:${resilience4jVersion}"
```

Чтобы Feign работал с Resilience4j Circuit Breaker необходимо доработать клиент:
```java
@FeignClient(name = "${client.producer.name}", configuration = ProducerClientConfiguration.class)
public interface ProducerClient {

    @RequestMapping(value = "/word", method = RequestMethod.GET)
    String getWord();
}
```

Реализовать класс конфигурации клиента:
```java
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
```
и класс fallback логики:
```java
@Component
public class FallbackProducerClient implements ProducerClient {

    @Override
    public String getWord() {
        return "Fallback";
    }
}
```

И далее в application.yaml файле написать конфигурацию для Circuit Breaker:
```yaml
resilience4j.circuitbreaker:
  configs:
    default:
      registerHealthIndicator: false
      slidingWindowSize: 10
      minimumNumberOfCalls: 5
      permittedNumberOfCallsInHalfOpenState: 3
      automaticTransitionFromOpenToHalfOpenEnabled: true
      waitDurationInOpenState: 2s
      failureRateThreshold: 50
      eventConsumerBufferSize: 10
      recordExceptions:
        - java.lang.Throwable
  instances:
    producer-service:
      baseConfig: default
```

По конфигурации из yaml файла в CircuitBreakerRegistry создается экземпляр CircuitBreaker по имени producer-service.

FallbackProducerClient нужен для того чтобы вернуть какой то запасной результат в случае если вызов основного клиента закончится ошибкой.

### Service Discovery and Load Balancing

Spring Cloud обеспечивает поддержку обнаружение сервисов и балансировку на стороне клиента.

Зависимости:
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-kubernetes'
implementation 'org.springframework.cloud:spring-cloud-starter-kubernetes-ribbon'
``` 

Для Open Feign дополнительные настройки не нужны.

kubernetes-ribbon поддерживает два режима - service, pod.

```yaml
spring:
  cloud:
    kubernetes:
      ribbon:
        mode: pod
```

- service - запросы сначала будут поступать в k8s сервисы,
- pod - запросы будут поступать непосредственно в поды(не поддерживается Istio и балансировка k8s)

*при использование разных модов нужно учитывать(изменять) настройки Resilience4j CircuitBreaker

## Reactive
### Http клиент
Для HTTP запросов в реактивных приложениях Spring используется WebClient.

Пример клиента:
```java
@Component
public class WordClient {
    private final WebClient webClient = WebClient.create("http://word-service");

    public Mono<String> getWord() {
        return webClient
                .get()
                .uri("/word")
                .retrieve()
                .bodyToMono(String.class);
    }
}
```
*настрокау таймаутов и тд смотреть в WordClient
### Circuit Breaker
Зависимости:
```groovy
set('resilience4jVersion', '1.1.0')

implementation "io.github.resilience4j:resilience4j-spring-boot2:${resilience4jVersion}"
implementation "io.github.resilience4j:resilience4j-reactor:${resilience4jVersion}"
```

Доработаем клиент:
```java
@Component
public class WordClient {
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public WordClient(@Value("${client.word.name}") String clientName,
                      CircuitBreakerRegistry registry) {
        this.circuitBreaker = registry.circuitBreaker(clientName);
        this.webClient = Webclient.create("http://" + clientName);
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
}
```

### Service Discovery and Load Balancing

Зависимости:
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-kubernetes'
implementation 'org.springframework.cloud:spring-cloud-starter-kubernetes-ribbon'
``` 

Доработки клиента:
```java
@Component
public class WordClient {
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public WordClient(@Value("${client.word.name}") String clientName,
                      CircuitBreakerRegistry registry,
                      WebClient.Builder webClientBuilder) {
        this.circuitBreaker = registry.circuitBreaker(clientName);
        this.webClient = webClientBuilder
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
}
```

Здесь мы инжектим WebClient.Builder который сконфигурирован специальным образом.

Конфиг WebClient.Builder bean:
```java
@Configuration
public class WebClientConfiguration {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

Аннотация @LoadBalanced магически добавляет всю логику по service discovery и load balancing для всех клиентов созданых из этого билдера.

## Configuration

Для конфигурации используется 3 .yaml файла.

- bootstrap.yaml - конфиг из этого файла читается в первую очередь
- application.yaml - здесь размещать основные дефолтные конфиги 
- application-local.yaml - файл конфигураци для запуска приложения на машине разработчика.

### ConfigMap

В k8s есть специальный ресур ConfigMap в которой можно хранить конфигурации для приложения.

Spring Boot поддерживает чтение этих конфигов см [документацию](https://cloud.spring.io/spring-cloud-static/spring-cloud-kubernetes/1.0.0.M2/multi/multi__configmap_propertysource.html)
 
Зависимость: 
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-kubernetes-config'
```

Также поддерживается горячая перезагрузка конфигураци если ресурс ConfigMap поменялся.

Конфиг для включения перезагрузки:
```yaml
spring:
  cloud:
    kubernetes:
      reload:
        enabled: true
        strategy: restart_context
```

Есть три режима:

- refresh - конфигурация обновится только в бинах помечены аннотациями @RefreshScope и @ConfigurationProperties
- restart_context - контекст приложения полностью перезагрузится с новыми параметрами
- shutdown - приложение завершит работу после чего k8s перезапустит контейнер

** при первом режиме не все конфиги могут обновится при двух последних надо учитывать что приложение некоторое время не 
будет обрабатывать запросы. Возможно лучшим вариантом будет написать свой [Operator](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/) 
который будет следить за ConfigMap и поочередно перезагружать Spring приложение


## Logging
При использование ELK стека есть смысл в лог писать сразу JSON который отправиться в LogStash без дополнительного парсинга

Зависимость:
```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:6.3'
```

Конфигурация:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProperty scope="context" name="application_name" source="spring.application.name"/>

    <springProfile name="!elk">
        <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>${CONSOLE_LOG_PATTERN}</pattern>
                <charset>utf8</charset>
            </encoder>
        </appender>

        <root level="info">
            <appender-ref ref="Console"/>
        </root>

    </springProfile>

    <springProfile name="elk">
        <appender name="jsonConsoleAppender" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>

        <root level="info">
            <appender-ref ref="jsonConsoleAppender"/>
        </root>
    </springProfile>
</configuration>
```

При таком конфиге если приложение запущено с профилем elk в лог будет писаться сразу JSON. Без elk профиля лог будет стандартным для Spring Boot приложения. 

## Monitoring

Мониторинг приложения с помощью Prometheus

Зависимости:
```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

Конфигурация:
```yaml
management:
  server:
    port: 8081
  endpoint:
    restart:
      enabled: true
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include:
          - health
          - info
          - prometheus
```



