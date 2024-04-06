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

package org.opengoofy.index12306.biz.orderservice.dao.algorithm;

import cn.hutool.core.collection.CollUtil;
import lombok.Getter;
import org.apache.shardingsphere.infra.util.exception.ShardingSpherePreconditions;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;
import org.apache.shardingsphere.sharding.exception.algorithm.sharding.ShardingAlgorithmInitializationException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;

/**
 * 订单数据库复合分片算法配置
 *
 * @公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class OrderCommonDataBaseComplexAlgorithm implements ComplexKeysShardingAlgorithm {

    @Getter
    private Properties props;

    private int shardingCount;
    private int tableShardingCount;

    private static final String SHARDING_COUNT_KEY = "sharding-count";
    private static final String TABLE_SHARDING_COUNT_KEY = "table-sharding-count";
    @Override
    public Collection<String> doSharding(Collection availableTargetNames, ComplexKeysShardingValue shardingValue) {
        Map<String, Collection<Comparable<Long>>> columnNameAndShardingValuesMap = shardingValue.getColumnNameAndShardingValuesMap();
        Collection<String> result = new LinkedHashSet<>(availableTargetNames.size());
        if (CollUtil.isNotEmpty(columnNameAndShardingValuesMap)) {
            String userId = "user_id";
            // 首先判断 SQL 是否包含用户 ID，如果包含直接取用户 ID 后六位
            Collection<Comparable<Long>> customerUserIdCollection = columnNameAndShardingValuesMap.get(userId);
            if (CollUtil.isNotEmpty(customerUserIdCollection)) {
                // 获取到 SQL 中包含的用户 ID 对应值
                Comparable<?> comparable = customerUserIdCollection.stream().findFirst().get();
                // 如果使用 MybatisPlus 因为传入时没有强类型判断，所以有可能用户 ID 是字符串，也可能是 Long 等数值
                // 比如传入的用户 ID 可能是 1683025552364568576 也可能是 '1683025552364568576'
                // 根据不同的值类型，做出不同的获取后六位判断。字符串直接截取后六位，Long 类型直接通过 % 运算获取后六位
                if (comparable instanceof String) {
                    String actualOrderSn = comparable.toString();
                    // 获取真实数据库的方法其实还是通过 HASH_MOD 方式取模的，shardingCount 就是咱们配置中的分库数量
                    result.add("ds_" + hashShardingValue(actualOrderSn.substring(Math.max(actualOrderSn.length() - 6, 0))) % shardingCount);
                } else {
                    String dbSuffix = String.valueOf(hashShardingValue((Long) comparable % 1000000) % shardingCount);
                    result.add("ds_" + dbSuffix);
                }
            } else {
                // 如果对订单中的 SQL 语句不包含用户 ID 那么就要从订单号中获取后六位，也就是用户 ID 后六位
                // 流程同用户 ID 获取流程
                String orderSn = "order_sn";
                Collection<Comparable<Long>> orderSnCollection = columnNameAndShardingValuesMap.get(orderSn);
                Comparable<?> comparable = orderSnCollection.stream().findFirst().get();
                if (comparable instanceof String) {
                    String actualOrderSn = comparable.toString();
                    result.add("ds_" + hashShardingValue(actualOrderSn.substring(Math.max(actualOrderSn.length() - 6, 0))) % shardingCount);
                } else {
                    result.add("ds_" + hashShardingValue((Long) comparable % 1000000) % shardingCount);
                }
            }
        }
        // 返回的是表名，
        return result;
    }
//    @Override
//    public Collection<String> doSharding(Collection availableTargetNames, ComplexKeysShardingValue shardingValue) {
//        Map<String, Collection<Comparable<Long>>> columnNameAndShardingValuesMap = shardingValue.getColumnNameAndShardingValuesMap();
//        Collection<String> result = new LinkedHashSet<>(availableTargetNames.size());
//        if (CollUtil.isNotEmpty(columnNameAndShardingValuesMap)) {
//            String userId = "user_id";
//            Collection<Comparable<Long>> customerUserIdCollection = columnNameAndShardingValuesMap.get(userId);
//            if (CollUtil.isNotEmpty(customerUserIdCollection)) {
//                String dbSuffix;
//                Comparable<?> comparable = customerUserIdCollection.stream().findFirst().get();
//                if (comparable instanceof String) {
//                    String actualUserId = comparable.toString();
//                    dbSuffix = String.valueOf(hashShardingValue(actualUserId.substring(Math.max(actualUserId.length() - 6, 0))) % shardingCount / tableShardingCount);
//                } else {
//                    dbSuffix = String.valueOf(hashShardingValue((Long) comparable % 1000000) % shardingCount / tableShardingCount);
//                }
//                result.add("ds_" + dbSuffix);
//            } else {
//                String orderSn = "order_sn";
//                String dbSuffix;
//                Collection<Comparable<Long>> orderSnCollection = columnNameAndShardingValuesMap.get(orderSn);
//                Comparable<?> comparable = orderSnCollection.stream().findFirst().get();
//                if (comparable instanceof String) {
//                    String actualOrderSn = comparable.toString();
//                    dbSuffix = String.valueOf(hashShardingValue(actualOrderSn.substring(Math.max(actualOrderSn.length() - 6, 0))) % shardingCount / tableShardingCount);
//                } else {
//                    dbSuffix = String.valueOf(hashShardingValue((Long) comparable % 1000000) % shardingCount / tableShardingCount);
//                }
//                result.add("ds_" + dbSuffix);
//            }
//        }
//        return result;
//    }

    @Override
    public void init(Properties props) {
        this.props = props;
        shardingCount = getShardingCount(props);
        tableShardingCount = getTableShardingCount(props);
    }

    private int getShardingCount(final Properties props) {
        ShardingSpherePreconditions.checkState(props.containsKey(SHARDING_COUNT_KEY), () -> new ShardingAlgorithmInitializationException(getType(), "Sharding count cannot be null."));
        return Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY));
    }

    private int getTableShardingCount(final Properties props) {
        ShardingSpherePreconditions.checkState(props.containsKey(TABLE_SHARDING_COUNT_KEY), () -> new ShardingAlgorithmInitializationException(getType(), "Table sharding count cannot be null."));
        return Integer.parseInt(props.getProperty(TABLE_SHARDING_COUNT_KEY));
    }

    private long hashShardingValue(final Comparable<?> shardingValue) {
        return Math.abs((long) shardingValue.hashCode());
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }
}
