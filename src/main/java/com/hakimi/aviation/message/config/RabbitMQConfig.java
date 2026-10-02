package com.hakimi.aviation.message.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
public class RabbitMQConfig {

    public static final String FLIGHT_BOOKING_ORDER_QUEUE = "flight.booking.order.queue";
    public static final String FLIGHT_BOOKING_ORDER_EXCHANGE = "flight.booking.order.exchange";
    //通配符 接收所有预订的请求
    public static final String FLIGHT_BOOKING_ORDER_BINDING = "flight.booking.order.#";
    //用来拼接所有的订单有关消息的路由键
    public static final String FLIGHT_BOOKING_BASE_ROUTING = "flight.booking.order.";



    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean("flightBookingQueue")
    public Queue flightBookingQueue(){
        return QueueBuilder.durable(FLIGHT_BOOKING_ORDER_QUEUE).build();
    }

    @Bean("flightBookingExchange")
    public TopicExchange flightBookingExchange(){
        return ExchangeBuilder.topicExchange(FLIGHT_BOOKING_ORDER_EXCHANGE).durable(true).build();
    }

    @Bean("flightBookingBinding")
    public Binding flightBookingBinding(@Qualifier("flightBookingQueue") Queue queue,
                                        @Qualifier("flightBookingExchange") TopicExchange exchange){

        return BindingBuilder.bind(queue).to(exchange).with(FLIGHT_BOOKING_ORDER_BINDING);
    }

    //以下是死信的三个组件 负责将未支付的订单放置15分钟
    public static final String ORDER_CANCEL_DEAD_QUEUE = "order.cancel.dead.queue";
    public static final String ORDER_CANCEL_DEAD_EXCHANGE = "order.cancel.dead.exchange";
    public static final String ORDER_CANCEL_DEAD_BINDING = "order.cancel.dead.#";
    public static final String ORDER_CANCEL_DEAD_BASE_BINDING = "order.cancel.dead.";
    //以下是负责取消订单的三个组件 负责消费死信
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_BINDING = "order.cancel.#";

    //死信队列 没有消费者消费 放置15分钟后转发给取消队列的交换机
    @Bean("orderCancelDeadQueue")
    public Queue orderCancelDeadQueue(){
        return QueueBuilder.durable(ORDER_CANCEL_DEAD_QUEUE)
                .withArgument("x-message-ttl",15*60*1000)  //15分钟
                .withArgument("x-dead-letter-exchange",ORDER_CANCEL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key","order.cancel.timeout")
                .build();
    }

    //死信交换机
    @Bean("orderCancelDeadExchange")
    public TopicExchange orderCancelDeadExchange(){
        return ExchangeBuilder.topicExchange(ORDER_CANCEL_DEAD_EXCHANGE).build();
    }

    //死信绑定
    @Bean("orderCancelDeadBinding")
    public Binding orderCancelDeadBinding(@Qualifier("orderCancelDeadQueue") Queue queue,
                                          @Qualifier("orderCancelDeadExchange") TopicExchange exchange){
        return BindingBuilder.bind(queue).to(exchange).with(ORDER_CANCEL_DEAD_BINDING);
    }

    //取消订单的队列，接收死信队列传来的消息 有专门的消费者负责消费其中消息
    @Bean("orderCancelQueue")
    public Queue orderCancelQueue(){
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    //接收死信队列消息发送到正常队列
    @Bean("orderCancelExchange")
    public TopicExchange orderCancelExchange(){
        return ExchangeBuilder.topicExchange(ORDER_CANCEL_EXCHANGE).build();
    }

    @Bean("orderCancelBinding")
    public Binding orderCancelBinding(@Qualifier("orderCancelQueue") Queue queue,
                                      @Qualifier("orderCancelExchange") TopicExchange exchange){
        return BindingBuilder.bind(queue).to(exchange).with(ORDER_CANCEL_BINDING);
    }

    //==== 负责接收退款消息的组件 =======

    // 交换机名称
    public static final String REFUND_EXCHANGE = "refund.direct.exchange";
    // 队列名称
    public static final String REFUND_QUEUE = "refund.execute.queue";
    // 路由键名称
    public static final String REFUND_ROUTING_KEY = "refund.execute";

    @Bean("refundQueue")
    public Queue refundQueue(){
        return QueueBuilder.durable(REFUND_QUEUE).build();
    }

    @Bean("refundDirectExchange")
    public DirectExchange refundDirectExchange(){
        return ExchangeBuilder.directExchange(REFUND_EXCHANGE).build();
    }

    @Bean("refundBinding")
    public Binding refundBinding(
            @Qualifier("refundQueue") Queue queue,
            @Qualifier("refundDirectExchange") DirectExchange directExchange
    ){
        return BindingBuilder.bind(queue).to(directExchange).with(REFUND_ROUTING_KEY);
    }


}
