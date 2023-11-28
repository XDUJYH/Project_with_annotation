/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengoofy.index12306.biz.orderservice.mq.produce;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;

import java.util.Optional;

/**
 * RocketMQ 抽象公共发送消息组件
 *
 * @公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCommonSendProduceTemplate<T> {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 构建消息发送事件基础扩充属性实体
     *
     * @param messageSendEvent 消息发送事件
     * @return 扩充属性实体
     */
    protected abstract BaseSendExtendDTO buildBaseSendExtendParam(T messageSendEvent);

    /**
     * 构建消息基本参数，请求头、Keys...
     *
     * @param messageSendEvent 消息发送事件
     * @param requestParam     扩充属性实体
     * @return 消息基本参数
     */
    protected abstract Message<?> buildMessage(T messageSendEvent, BaseSendExtendDTO requestParam);

    /**
     * 消息事件通用发送
     *
     * @param messageSendEvent 消息发送事件
     * @return 消息发送返回结果
     */
    public SendResult sendMessage(T messageSendEvent) {
        BaseSendExtendDTO baseSendExtendDTO = buildBaseSendExtendParam(messageSendEvent);
        SendResult sendResult;
        try {
            //尝试发送消息：
            //构建消息的目的地（destination），这包括主题（topic）和可能的标签（tag）。
            //调用 rocketMQTemplate.syncSend 方法发送消息，其中包括目的地、消息体、发送超时时间、延时级别等参数。
            //将发送结果赋值给 sendResult 变量。
            StringBuilder destinationBuilder = StrUtil.builder().append(baseSendExtendDTO.getTopic());
            if (StrUtil.isNotBlank(baseSendExtendDTO.getTag())) {
                destinationBuilder.append(":").append(baseSendExtendDTO.getTag());
            }

//            syncSend 是 RocketMQ 提供的同步发送消息的方法。在你提供的代码中，rocketMQTemplate 是 RocketMQ 提供的消息发送模板，syncSend 方法用于同步发送消息。具体来说，这个方法的参数包括：
//            目的地字符串 (destinationBuilder.toString())：
//            这是消息要发送到的目的地，通常包括主题（topic）和可能的标签（tag）。destinationBuilder.toString() 返回的是目的地的字符串表示。

//            消息对象 (buildMessage(messageSendEvent, baseSendExtendDTO))：
//            这是要发送的消息对象，包括消息体、消息的唯一标识等信息。这个消息对象是通过 buildMessage 方法构建的

//            发送超时时间 (baseSendExtendDTO.getSentTimeout())：
//            这是消息发送的超时时间，即发送消息的最大等待时间。

//            消息的延时级别 (Optional.ofNullable(baseSendExtendDTO.getDelayLevel()).orElse(0))：
//            这是 RocketMQ 支持的消息延时发送的级别。如果不需要延时，可以将其设置为 0。

//            syncSend 方法是一种阻塞的发送方式，它会等待消息发送结果，直到发送成功或者发送超时。这种方式适用于一些同步场景，例如需要获取发送结果并根据结果进行后续处理的情况。但需要注意的是，由于是同步发送，调用方会被阻塞，可能影响系统的响应性能。在一些异步场景中，可能会选择使用异步发送的方式。
            sendResult = rocketMQTemplate.syncSend(
                    destinationBuilder.toString(),
                    buildMessage(messageSendEvent, baseSendExtendDTO),
                    baseSendExtendDTO.getSentTimeout(),
                    Optional.ofNullable(baseSendExtendDTO.getDelayLevel()).orElse(0)
            );
            log.info("[{}] 消息发送结果：{}，消息ID：{}，消息Keys：{}", baseSendExtendDTO.getEventName(), sendResult.getSendStatus(), sendResult.getMsgId(), baseSendExtendDTO.getKeys());
        } catch (Throwable ex) {
            log.error("[{}] 消息发送失败，消息体：{}", baseSendExtendDTO.getEventName(), JSON.toJSONString(messageSendEvent), ex);
            throw ex;
        }
        return sendResult;
    }
}
