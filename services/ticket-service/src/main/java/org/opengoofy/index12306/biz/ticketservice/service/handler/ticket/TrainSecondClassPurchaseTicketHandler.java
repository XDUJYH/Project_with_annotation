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

package org.opengoofy.index12306.biz.ticketservice.service.handler.ticket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Pair;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.biz.ticketservice.common.enums.VehicleSeatTypeEnum;
import org.opengoofy.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.TrainSeatBaseDTO;
import org.opengoofy.index12306.biz.ticketservice.service.SeatService;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.base.AbstractTrainPurchaseTicketTemplate;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.select.SeatSelection;
import org.opengoofy.index12306.biz.ticketservice.toolkit.SeatNumberUtil;
import org.opengoofy.index12306.framework.starter.convention.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高铁二等座购票组件
 *
 * @公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Component
@RequiredArgsConstructor
public class TrainSecondClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {

    private final SeatService seatService;

    private static final Map<Character, Integer> SEAT_Y_INT = Map.of('A', 0, 'B', 1, 'C', 2, 'D', 3, 'F', 4);

    @Override
    public String mark() {
        return VehicleTypeEnum.HIGH_SPEED_RAIN.getName() + VehicleSeatTypeEnum.SECOND_CLASS.getName();
    }

    @Override
    protected List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam) {
        String trainId = requestParam.getRequestParam().getTrainId();
        String departure = requestParam.getRequestParam().getDeparture();
        String arrival = requestParam.getRequestParam().getArrival();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        //查询列车有余票的车厢号集合
        List<String> trainCarriageList = seatService.listUsableCarriageNumber(trainId, requestParam.getSeatType(), departure, arrival);
        //获取列车车厢余票集合
        List<Integer> trainStationCarriageRemainingTicket = seatService.listSeatRemainingTicket(trainId, departure, arrival, trainCarriageList);
        int remainingTicketSum = trainStationCarriageRemainingTicket.stream().mapToInt(Integer::intValue).sum();
        if (remainingTicketSum < passengerSeatDetails.size()) {
            throw new ServiceException("站点余票不足，请尝试更换座位类型或选择其它站点");
        }
        if (passengerSeatDetails.size() < 6) {
            if (CollUtil.isNotEmpty(requestParam.getRequestParam().getChooseSeats())) {
                return findMatchSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket).getKey();
            }
            return selectSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
        } else {
            if (CollUtil.isNotEmpty(requestParam.getRequestParam().getChooseSeats())) {
                return findMatchSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket).getKey();
            }
            return selectComplexSeats(requestParam, trainCarriageList, trainStationCarriageRemainingTicket);
        }
    }

    private List<Pair<Integer, Integer>> calcChooseSeatLevelPairList(int[][] actualSeats, List<String> chooseSeatList) {
        //获取第一个选定的位置
        String firstChooseSeat = chooseSeatList.get(0);
        int firstSeatX = Integer.parseInt(firstChooseSeat.substring(1));
        int firstSeatY = SEAT_Y_INT.get(firstChooseSeat.charAt(0));
        //如D0这个座位号，处理后firstSeatX=0，firstSeatY=3,因此X对应着第一排还是第二排，Y对应着ABCDF这五个座位选了哪个
        List<Pair<Integer, Integer>> chooseSeatLevelPairList = new ArrayList<>();
        chooseSeatLevelPairList.add(new Pair<>(firstSeatX, firstSeatY));
        int minLevelX = 0;
        for (int i = 1; i < chooseSeatList.size(); i++) {
            String chooseSeat = chooseSeatList.get(i);
            int chooseSeatX = Integer.parseInt(chooseSeat.substring(1));
            int chooseSeatY = SEAT_Y_INT.get(chooseSeat.charAt(0));
            minLevelX = Math.min(minLevelX, chooseSeatX - firstSeatX);
            chooseSeatLevelPairList.add(new Pair<>(chooseSeatX - firstSeatX, chooseSeatY - firstSeatY));
        }
        //分析：上面这个循环将每个选择的位置与第一个选择的位置进行了比较，并将比较后的结果存入了chooseSeatLevelPairList，代表着相对第一个位置的相对位置
        //int i = Math.abs(minLevelX)，目前测试的好像都是从0开始
        for (int i = Math.abs(minLevelX); i < 18; i++) {
            List<Pair<Integer, Integer>> sureSeatList = new ArrayList<>();
            //判断actualSeats[i][firstSeatY]是否可以使用
            if (actualSeats[i][firstSeatY] == 0) {
                sureSeatList.add(new Pair<>(i, firstSeatY));
                //先确定第一个座位的位置，然后依次遍历以后的位置是否存在满足条件的
                for (int j = 1; j < chooseSeatList.size(); j++) {
                    Pair<Integer, Integer> pair = chooseSeatLevelPairList.get(j);
                    int chooseSeatX = pair.getKey();
                    int chooseSeatY = pair.getValue();
                    int x = i + chooseSeatX;
                    //如果x超过18排则无法在这节车厢安排座位
                    if (x >= 18) {
                        return Collections.emptyList();
                    }
                    if (actualSeats[i + chooseSeatX][firstSeatY + chooseSeatY] == 0) {
                        sureSeatList.add(new Pair<>(i + chooseSeatX, firstSeatY + chooseSeatY));
                    } else {
                        break;
                    }
                }
            }
            if (sureSeatList.size() == chooseSeatList.size()) {
                return sureSeatList;
            }
        }
        return Collections.emptyList();
    }

    private Pair<List<TrainPurchaseTicketRespDTO>, Boolean> findMatchSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        TrainSeatBaseDTO trainSeatBaseDTO = buildTrainSeatBaseDTO(requestParam);
        //创建一个ArrayList实例并指定其初始容量。
        List<TrainPurchaseTicketRespDTO> actualResult = Lists.newArrayListWithCapacity(trainSeatBaseDTO.getPassengerSeatDetails().size());
        HashMap<String, List<Pair<Integer, Integer>>> carriagesSeatMap = new HashMap<>(16);
        int passengersNumber = trainSeatBaseDTO.getPassengerSeatDetails().size();
        //trainStationCarriageRemainingTicket代表每个车厢的余票数，它的长度就是含有余票的车厢数
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            //获取车厢号
            String carriagesNumber = trainCarriageList.get(i);
            //获取可用的座位号，并存到list中，如'01A'、'01B'这样的可用座位号
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainSeatBaseDTO.getTrainId(), carriagesNumber, requestParam.getSeatType(), trainSeatBaseDTO.getDeparture(), trainSeatBaseDTO.getArrival());
            //这个数组代表这个车厢所有的座位
            int[][] actualSeats = new int[18][5];
            List<Pair<Integer, Integer>> carriagesVacantSeat = new ArrayList<>();
            for (int j = 1; j < 19; j++) {
                for (int k = 1; k < 6; k++) {
                    if (j <= 9) {
                        //逻辑是遍历这个车厢的所有座位，然后挨个判断这个作为是否被包含在listAvailableSeat中，包含则为actualSeats这个位置赋值为0，否则为1
                        actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(2, k)) ? 0 : 1;
                    } else {
                        actualSeats[j - 1][k - 1] = listAvailableSeat.contains("" + j + SeatNumberUtil.convert(2, k)) ? 0 : 1;
                    }
                    if (actualSeats[j - 1][k - 1] == 0) {
                        //为0说明可用，将其存入
                        carriagesVacantSeat.add(new Pair<>(j - 1, k - 1));
                    }
                }
            }
            //经过以上步骤后，carriagesVacantSeat存入了可用的座位坐标
            List<String> selectSeats = new ArrayList<>(passengersNumber);
            //calcChooseSeatLevelPairList作用是取得满足选择座位条件的结果
            List<Pair<Integer, Integer>> sureSeatList = calcChooseSeatLevelPairList(actualSeats, trainSeatBaseDTO.getChooseSeatList());
            if (CollUtil.isNotEmpty(sureSeatList) && carriagesVacantSeat.size() >= passengersNumber) {
                List<Pair<Integer, Integer>> vacantSeatList = new ArrayList<>();
                //不相等的情况是可以存在的，比如3个人买票只选了两个座位
                if (sureSeatList.size() != passengersNumber) {
                    for (int i1 = 0; i1 < sureSeatList.size(); i1++) {
                        Pair<Integer, Integer> pair = sureSeatList.get(i1);
                        actualSeats[pair.getKey()][pair.getValue()] = 1;
                    }
                    for (int i1 = 0; i1 < 18; i1++) {
                        for (int j = 0; j < 5; j++) {
                            if (actualSeats[i1][j] == 0) {
                                vacantSeatList.add(new Pair<>(i1, j));
                            }
                        }
                    }
                    //经过上面这个循环后vacantSeatList存入了可用的座位对
                    //获得未选位置的人数
                    int needSeatSize = passengersNumber - sureSeatList.size();
                    //直接将可用的前needSeatSize放到sureSeatList中
                    sureSeatList.addAll(vacantSeatList.subList(0, needSeatSize));
                }
                //TODO waiting to read
                for (Pair<Integer, Integer> each : sureSeatList) {
                    if (each.getKey() <= 8) {
                        selectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                    } else {
                        selectSeats.add("" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                    }
                }
                AtomicInteger countNum = new AtomicInteger(0);
                for (String selectSeat : selectSeats) {
                    TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                    PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                    result.setSeatNumber(selectSeat);
                    result.setSeatType(currentTicketPassenger.getSeatType());
                    result.setCarriageNumber(carriagesNumber);
                    result.setPassengerId(currentTicketPassenger.getPassengerId());
                    actualResult.add(result);
                }
                return new Pair<>(actualResult, Boolean.TRUE);
            } else {
                if (CollUtil.isNotEmpty(carriagesVacantSeat)) {
                    carriagesSeatMap.put(carriagesNumber, carriagesVacantSeat);
                    if (i == trainStationCarriageRemainingTicket.size() - 1) {
                        Pair<String, List<Pair<Integer, Integer>>> findSureCarriageSeat = null;
                        for (Map.Entry<String, List<Pair<Integer, Integer>>> entry : carriagesSeatMap.entrySet()) {
                            if (entry.getValue().size() >= passengersNumber) {
                                findSureCarriageSeat = new Pair<>(entry.getKey(), entry.getValue().subList(0, passengersNumber));
                                break;
                            }
                        }
                        if (findSureCarriageSeat != null) {
                            for (Pair<Integer, Integer> each : findSureCarriageSeat.getValue()) {
                                if (each.getKey() <= 8) {
                                    selectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                                } else {
                                    selectSeats.add("" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                                }
                            }
                            AtomicInteger countNum = new AtomicInteger(0);
                            for (String selectSeat : selectSeats) {
                                TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                result.setSeatNumber(selectSeat);
                                result.setSeatType(currentTicketPassenger.getSeatType());
                                result.setCarriageNumber(findSureCarriageSeat.getKey());
                                result.setPassengerId(currentTicketPassenger.getPassengerId());
                                actualResult.add(result);
                            }
                            return new Pair<>(actualResult, Boolean.TRUE);
                        } else {
                            int sureSeatListSize = 0;
                            AtomicInteger countNum = new AtomicInteger(0);
                            for (Map.Entry<String, List<Pair<Integer, Integer>>> entry : carriagesSeatMap.entrySet()) {
                                if (sureSeatListSize < passengersNumber) {
                                    if (sureSeatListSize + entry.getValue().size() < passengersNumber) {
                                        sureSeatListSize = sureSeatListSize + entry.getValue().size();
                                        List<String> actualSelectSeats = new ArrayList<>();
                                        for (Pair<Integer, Integer> each : entry.getValue()) {
                                            if (each.getKey() <= 8) {
                                                actualSelectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                                            } else {
                                                actualSelectSeats.add("" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                                            }
                                        }
                                        for (String selectSeat : actualSelectSeats) {
                                            TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                            PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                            result.setSeatNumber(selectSeat);
                                            result.setSeatType(currentTicketPassenger.getSeatType());
                                            result.setCarriageNumber(entry.getKey());
                                            result.setPassengerId(currentTicketPassenger.getPassengerId());
                                            actualResult.add(result);
                                        }
                                    } else {
                                        int needSeatSize = entry.getValue().size() - (sureSeatListSize + entry.getValue().size() - passengersNumber);
                                        sureSeatListSize = sureSeatListSize + needSeatSize;
                                        if (sureSeatListSize >= passengersNumber) {
                                            List<String> actualSelectSeats = new ArrayList<>();
                                            for (Pair<Integer, Integer> each : entry.getValue().subList(0, needSeatSize)) {
                                                if (each.getKey() <= 8) {
                                                    actualSelectSeats.add("0" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                                                } else {
                                                    actualSelectSeats.add("" + (each.getKey() + 1) + SeatNumberUtil.convert(2, each.getValue() + 1));
                                                }
                                            }
                                            for (String selectSeat : actualSelectSeats) {
                                                TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                                                PurchaseTicketPassengerDetailDTO currentTicketPassenger = trainSeatBaseDTO.getPassengerSeatDetails().get(countNum.getAndIncrement());
                                                result.setSeatNumber(selectSeat);
                                                result.setSeatType(currentTicketPassenger.getSeatType());
                                                result.setCarriageNumber(entry.getKey());
                                                result.setPassengerId(currentTicketPassenger.getPassengerId());
                                                actualResult.add(result);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            return new Pair<>(actualResult, Boolean.TRUE);
                        }
                    }
                }
            }
        }
        return new Pair<>(null, Boolean.FALSE);
    }

    private List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        String trainId = requestParam.getRequestParam().getTrainId();
        String departure = requestParam.getRequestParam().getDeparture();
        String arrival = requestParam.getRequestParam().getArrival();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        List<TrainPurchaseTicketRespDTO> actualResult = new ArrayList<>();
        Map<String, Integer> demotionStockNumMap = new LinkedHashMap<>();
        Map<String, int[][]> actualSeatsMap = new HashMap<>();
        Map<String, int[][]> carriagesNumberSeatsMap = new HashMap<>();
        String carriagesNumber;
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            carriagesNumber = trainCarriageList.get(i);
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainId, carriagesNumber, requestParam.getSeatType(), departure, arrival);
            int[][] actualSeats = new int[18][5];
            for (int j = 1; j < 19; j++) {
                for (int k = 1; k < 6; k++) {
                    // 当前默认按照复兴号商务座排序，后续这里需要按照简单工厂对车类型进行获取 y 轴
                    if (j <= 9) {
                        actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(2, k)) ? 0 : 1;
                    } else {
                        actualSeats[j - 1][k - 1] = listAvailableSeat.contains("" + j + SeatNumberUtil.convert(2, k)) ? 0 : 1;
                    }
                }
            }
            int[][] select = SeatSelection.adjacent(passengerSeatDetails.size(), actualSeats);
            if (select != null) {
                carriagesNumberSeatsMap.put(carriagesNumber, select);
                break;
            }
            int demotionStockNum = 0;
            for (int[] actualSeat : actualSeats) {
                for (int i1 : actualSeat) {
                    if (i1 == 0) {
                        demotionStockNum++;
                    }
                }
            }
            demotionStockNumMap.putIfAbsent(carriagesNumber, demotionStockNum);
            actualSeatsMap.putIfAbsent(carriagesNumber, actualSeats);
            if (i < trainStationCarriageRemainingTicket.size() - 1) {
                continue;
            }
            // 如果邻座算法无法匹配，尝试对用户进行降级分配：同车厢不邻座
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                int demotionStockNumBack = entry.getValue();
                if (demotionStockNumBack > passengerSeatDetails.size()) {
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(passengerSeatDetails.size(), seats);
                    if (Objects.equals(nonAdjacentSeats.length, passengerSeatDetails.size())) {
                        select = nonAdjacentSeats;
                        carriagesNumberSeatsMap.put(carriagesNumberBack, select);
                        break;
                    }
                }
            }
            // 如果同车厢也已无法匹配，则对用户座位再次降级：不同车厢不邻座
            if (Objects.isNull(select)) {
                for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                    String carriagesNumberBack = entry.getKey();
                    int demotionStockNumBack = entry.getValue();
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(demotionStockNumBack, seats);
                    carriagesNumberSeatsMap.put(entry.getKey(), nonAdjacentSeats);
                }
            }
        }
        // 乘车人员在单一车厢座位不满足，触发乘车人元分布在不同车厢
        int count = (int) carriagesNumberSeatsMap.values().stream()
                .flatMap(Arrays::stream)
                .count();
        if (CollUtil.isNotEmpty(carriagesNumberSeatsMap) && passengerSeatDetails.size() == count) {
            int countNum = 0;
            for (Map.Entry<String, int[][]> entry : carriagesNumberSeatsMap.entrySet()) {
                List<String> selectSeats = new ArrayList<>();
                for (int[] ints : entry.getValue()) {
                    if (ints[0] <= 9) {
                        selectSeats.add("0" + ints[0] + SeatNumberUtil.convert(2, ints[1]));
                    } else {
                        selectSeats.add("" + ints[0] + SeatNumberUtil.convert(2, ints[1]));
                    }
                }
                for (String selectSeat : selectSeats) {
                    TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                    PurchaseTicketPassengerDetailDTO currentTicketPassenger = passengerSeatDetails.get(countNum++);
                    result.setSeatNumber(selectSeat);
                    result.setSeatType(currentTicketPassenger.getSeatType());
                    result.setCarriageNumber(entry.getKey());
                    result.setPassengerId(currentTicketPassenger.getPassengerId());
                    actualResult.add(result);
                }
            }
        }
        return actualResult;
    }

    private List<TrainPurchaseTicketRespDTO> selectComplexSeats(SelectSeatDTO requestParam, List<String> trainCarriageList, List<Integer> trainStationCarriageRemainingTicket) {
        String trainId = requestParam.getRequestParam().getTrainId();
        String departure = requestParam.getRequestParam().getDeparture();
        String arrival = requestParam.getRequestParam().getArrival();
        List<PurchaseTicketPassengerDetailDTO> passengerSeatDetails = requestParam.getPassengerSeatDetails();
        List<TrainPurchaseTicketRespDTO> actualResult = new ArrayList<>();
        Map<String, Integer> demotionStockNumMap = new LinkedHashMap<>();
        Map<String, int[][]> actualSeatsMap = new HashMap<>();
        Map<String, int[][]> carriagesNumberSeatsMap = new HashMap<>();
        String carriagesNumber;
        // 多人分配同一车厢邻座
        for (int i = 0; i < trainStationCarriageRemainingTicket.size(); i++) {
            carriagesNumber = trainCarriageList.get(i);
            List<String> listAvailableSeat = seatService.listAvailableSeat(trainId, carriagesNumber, requestParam.getSeatType(), departure, arrival);
            int[][] actualSeats = new int[18][5];
            for (int j = 1; j < 19; j++) {
                for (int k = 1; k < 6; k++) {
                    // 当前默认按照复兴号商务座排序，后续这里需要按照简单工厂对车类型进行获取 y 轴
                    if (j <= 9) {
                        actualSeats[j - 1][k - 1] = listAvailableSeat.contains("0" + j + SeatNumberUtil.convert(2, k)) ? 0 : 1;
                    } else {
                        actualSeats[j - 1][k - 1] = listAvailableSeat.contains("" + j + SeatNumberUtil.convert(2, k)) ? 0 : 1;
                    }
                }
            }
            int[][] actualSeatsTranscript = deepCopy(actualSeats);
            List<int[][]> actualSelects = new ArrayList<>();
            List<List<PurchaseTicketPassengerDetailDTO>> splitPassengerSeatDetails = ListUtil.split(passengerSeatDetails, 3);
            for (List<PurchaseTicketPassengerDetailDTO> each : splitPassengerSeatDetails) {
                int[][] select = SeatSelection.adjacent(each.size(), actualSeatsTranscript);
                if (select != null) {
                    for (int[] ints : select) {
                        actualSeatsTranscript[ints[0] - 1][ints[1] - 1] = 1;
                    }
                    actualSelects.add(select);
                }
            }
            if (actualSelects.size() == splitPassengerSeatDetails.size()) {
                int[][] actualSelect = null;
                for (int j = 0; j < actualSelects.size(); j++) {
                    if (j == 0) {
                        actualSelect = mergeArrays(actualSelects.get(j), actualSelects.get(j + 1));
                    }
                    if (j != 0 && actualSelects.size() > 2) {
                        actualSelect = mergeArrays(actualSelect, actualSelects.get(j + 1));
                    }
                }
                carriagesNumberSeatsMap.put(carriagesNumber, actualSelect);
                break;
            }
            int demotionStockNum = 0;
            for (int[] actualSeat : actualSeats) {
                for (int i1 : actualSeat) {
                    if (i1 == 0) {
                        demotionStockNum++;
                    }
                }
            }
            demotionStockNumMap.putIfAbsent(carriagesNumber, demotionStockNum);
            actualSeatsMap.putIfAbsent(carriagesNumber, actualSeats);
        }
        // 如果邻座算法无法匹配，尝试对用户进行降级分配：同车厢不邻座
        if (CollUtil.isEmpty(carriagesNumberSeatsMap)) {
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                int demotionStockNumBack = entry.getValue();
                if (demotionStockNumBack > passengerSeatDetails.size()) {
                    int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                    int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(passengerSeatDetails.size(), seats);
                    if (Objects.equals(nonAdjacentSeats.length, passengerSeatDetails.size())) {
                        carriagesNumberSeatsMap.put(carriagesNumberBack, nonAdjacentSeats);
                        break;
                    }
                }
            }
        }
        // 如果同车厢也已无法匹配，则对用户座位再次降级：不同车厢不邻座
        if (CollUtil.isEmpty(carriagesNumberSeatsMap)) {
            int undistributedPassengerSize = passengerSeatDetails.size();
            for (Map.Entry<String, Integer> entry : demotionStockNumMap.entrySet()) {
                String carriagesNumberBack = entry.getKey();
                int demotionStockNumBack = entry.getValue();
                int[][] seats = actualSeatsMap.get(carriagesNumberBack);
                int[][] nonAdjacentSeats = SeatSelection.nonAdjacent(Math.min(undistributedPassengerSize, demotionStockNumBack), seats);
                undistributedPassengerSize = undistributedPassengerSize - demotionStockNumBack;
                carriagesNumberSeatsMap.put(entry.getKey(), nonAdjacentSeats);
            }
        }
        // 乘车人员在单一车厢座位不满足，触发乘车人元分布在不同车厢
        int count = (int) carriagesNumberSeatsMap.values().stream()
                .flatMap(Arrays::stream)
                .count();
        if (CollUtil.isNotEmpty(carriagesNumberSeatsMap) && passengerSeatDetails.size() == count) {
            int countNum = 0;
            for (Map.Entry<String, int[][]> entry : carriagesNumberSeatsMap.entrySet()) {
                List<String> selectSeats = new ArrayList<>();
                for (int[] ints : entry.getValue()) {
                    if (ints[0] <= 9) {
                        selectSeats.add("0" + ints[0] + SeatNumberUtil.convert(2, ints[1]));
                    } else {
                        selectSeats.add("" + ints[0] + SeatNumberUtil.convert(2, ints[1]));
                    }
                }
                for (String selectSeat : selectSeats) {
                    TrainPurchaseTicketRespDTO result = new TrainPurchaseTicketRespDTO();
                    PurchaseTicketPassengerDetailDTO currentTicketPassenger = passengerSeatDetails.get(countNum++);
                    result.setSeatNumber(selectSeat);
                    result.setSeatType(currentTicketPassenger.getSeatType());
                    result.setCarriageNumber(entry.getKey());
                    result.setPassengerId(currentTicketPassenger.getPassengerId());
                    actualResult.add(result);
                }
            }
        }
        return actualResult;
    }

    public static int[][] mergeArrays(int[][] array1, int[][] array2) {
        List<int[]> list = new ArrayList<>(Arrays.asList(array1));
        list.addAll(Arrays.asList(array2));
        return list.toArray(new int[0][]);
    }

    public static int[][] deepCopy(int[][] originalArray) {
        int[][] copy = new int[originalArray.length][originalArray[0].length];
        for (int i = 0; i < originalArray.length; i++) {
            System.arraycopy(originalArray[i], 0, copy[i], 0, originalArray[i].length);
        }
        return copy;
    }
}
