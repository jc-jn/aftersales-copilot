package com.aftersales.copilot.aiadapter.messaging;
import org.springframework.amqp.core.*; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
@Configuration public class RabbitMessagingConfig {
 @Bean TopicExchange aiExchange(){return new TopicExchange("aftersales.topic",true,false);}
 @Bean Queue aiAnalysisQueue(){return QueueBuilder.durable("ai.ticket.analysis.q").withArgument("x-dead-letter-exchange","aftersales.dlx").build();}
 @Bean Binding aiAnalysisBinding(Queue aiAnalysisQueue,TopicExchange aiExchange){return BindingBuilder.bind(aiAnalysisQueue).to(aiExchange).with("ticket.ai.analyze.requested.v1");}
 @Bean TopicExchange deadLetterExchange(){return new TopicExchange("aftersales.dlx",true,false);}
}
