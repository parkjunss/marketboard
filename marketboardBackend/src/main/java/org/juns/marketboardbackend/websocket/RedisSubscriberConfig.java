package org.juns.marketboardbackend.websocket;

import org.juns.marketboardbackend.alert.AlertTriggerSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisQuoteSubscriber quoteSubscriber,
            AlertTriggerSubscriber alertTriggerSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(quoteSubscriber, new ChannelTopic("quotes"));
        container.addMessageListener(alertTriggerSubscriber, new ChannelTopic("alert-triggers"));
        return container;
    }
}
