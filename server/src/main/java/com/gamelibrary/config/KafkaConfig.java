package com.gamelibrary.config;

import com.gamelibrary.kafka.event.GameEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Typed producer factory + template for our event records.
     * The auto-configured KafkaTemplate is KafkaTemplate&lt;Object, Object&gt;;
     * this one gives compile-time safety on keys and event payloads.
     */
    @Bean
    public ProducerFactory<String, GameEvent> producerFactory() {
        return new DefaultKafkaProducerFactory<>(
                java.util.Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
                ));
    }

    @Bean
    public KafkaTemplate<String, GameEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * One topic per event stream. Spring Boot auto-creates topics when the
     * broker has auto-creation enabled (the docker-compose dev broker does).
     * Declaring them explicitly keeps intent visible and replicas/Partitions
     * under our control.
     */
    @Bean
    public NewTopic gameCreatedTopic() {
        return TopicBuilder.name("game-library.games.created")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic libraryStatusTopic() {
        return TopicBuilder.name("game-library.library.status-changed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic listUpdatedTopic() {
        return TopicBuilder.name("game-library.lists.updated")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
