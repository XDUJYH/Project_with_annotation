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

package org.opengoofy.index12306.biz.orderservice.service.orderid;

/**
 * 全局唯一订单号生成器
 *
 * @公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class DistributedIdGenerator {

    //1609459200000L 是一个长整型数字，它表示的是自1970年1月1日 00:00:00 UTC（协调世界时）以来的毫秒数。这个时间戳被广泛用于计算机科学和软件开发中，通常被称为 "Unix时间戳" 或 "Epoch时间"。
    private static final long EPOCH = 1609459200000L;
    private static final int NODE_BITS = 5;
    private static final int SEQUENCE_BITS = 7;

    private final long nodeID;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public DistributedIdGenerator(long nodeID) {
        this.nodeID = nodeID;
    }

    public synchronized long generateId() {
        //如果当前时间戳小于上一个生成ID的时间戳，表示时钟发生了回退（可能由于系统时间调整），抛出异常。
        long timestamp = System.currentTimeMillis() - EPOCH;
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        //如果当前时间戳和上一个时间戳相同，说明在同一毫秒内多次生成ID。
        //递增序列号，但通过与 ((1 << SEQUENCE_BITS) - 1) 位掩码来限制序列号的范围。(1 << SEQUENCE_BITS 表示将1左移 SEQUENCE_BITS 位，然后减去1，得到一个形如 111...111 的二进制数，其中有 SEQUENCE_BITS 个1。1 << SEQUENCE_BITS 表示将二进制数 1 左移 SEQUENCE_BITS 位。在这个情况下，即 1 << 7，得到二进制数 10000000。- 1 操作将二进制数 10000000 减去 1，得到二进制数 01111111。)
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
            if (sequence == 0) {
        //如果序列号达到上限，等待下一个毫秒（tilNextMillis 方法用于等待直到下一个毫秒）。
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
        //如果当前时间戳不同，重置序列号为0。
            sequence = 0L;
        }
        //更新上一个时间戳，生成最终ID：
        lastTimestamp = timestamp;
        //将当前时间戳左移（<<） NODE_BITS + SEQUENCE_BITS 位，腾出位置给节点ID和序列号。
        //将节点ID左移 SEQUENCE_BITS 位。
        //最后，将这三个部分通过位运算组合起来，形成最终的ID。
        return (timestamp << (NODE_BITS + SEQUENCE_BITS)) | (nodeID << SEQUENCE_BITS) | sequence;
        //这是生成的两个示例（由于sequence我没模拟一毫秒点很多次，所以最低7位是0）：
        // 0b00000001_01010101_10011010_01010100_01100110_01110011_00000000
        // 0b00000001_01010101_10011010_01011111_00110111_11110011_00000000
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - EPOCH;
        }
        return timestamp;
    }
}
