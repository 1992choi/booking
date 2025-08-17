package com.example.stock.service.trade;

import com.example.stock.domain.market.TradeHistory;
import com.example.stock.dto.TradeHistoryDto;
import com.example.stock.repository.market.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeHistoryService {

    private final TradeHistoryRepository tradeHistoryRepository;

    @Transactional
    public TradeHistory save(TradeHistoryDto tradeHistoryDto) {
        return tradeHistoryRepository.save(tradeHistoryDto.toEntity());
    }

    @Transactional
    public void delete() {
        tradeHistoryRepository.deleteAll();
    }

    @Transactional
    public List<TradeHistoryDto> getTradeHistories() {
        List<TradeHistory> trades = tradeHistoryRepository.findByIsSold(false);

        return trades.stream()
                .map(TradeHistoryDto::fromEntity)
                .collect(Collectors.toList());
    }

}
