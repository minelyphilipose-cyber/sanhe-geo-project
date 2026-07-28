package com.huanjing.geo.module.retention.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PollRetentionSliceWriteService {

    private final PollRetentionSliceGuardService sliceGuardService;

    @Transactional
    public void execute(Long projectId,
                        LocalDate batchDate,
                        String questionTier,
                        Runnable operation) {
        execute(projectId, batchDate, questionTier, () -> {
            operation.run();
            return null;
        });
    }

    @Transactional
    public <T> T execute(Long projectId,
                         LocalDate batchDate,
                         String questionTier,
                         Supplier<T> operation) {
        sliceGuardService.lockAndRequireWritable(projectId, batchDate, questionTier);
        return operation.get();
    }
}
