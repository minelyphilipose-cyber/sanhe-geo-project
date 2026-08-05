package com.huanjing.geo.module.dispatch.service;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
public class PollDatabaseWriteRetryService {

    private static final int MAX_ATTEMPTS = 3;

    public <T> T execute(String operation, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                if (!isTransientLockFailure(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt, operation);
                }
            }
        }
        String reason = lastFailure == null || lastFailure.getMessage() == null
                ? "unknown lock conflict"
                : lastFailure.getMessage();
        throw new PollResultPersistenceBusyException(
                operation + " remained busy after " + MAX_ATTEMPTS + " attempts: " + reason,
                lastFailure);
    }

    public void run(String operation, Runnable action) {
        execute(operation, () -> {
            action.run();
            return null;
        });
    }

    boolean isTransientLockFailure(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof DeadlockLoserDataAccessException
                    || current instanceof PessimisticLockingFailureException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 1205
                    || sqlException.getErrorCode() == 1213
                    || sqlException.getErrorCode() == 3572
                    || "40001".equals(sqlException.getSQLState()))) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("Deadlock found")
                    || message.contains("Lock wait timeout exceeded")
                    || message.contains("could not be acquired immediately"))) {
                return true;
            }
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt, String operation) {
        long minDelayMs = attempt == 1 ? 10L : 30L;
        long maxDelayMs = attempt == 1 ? 30L : 80L;
        long delayMs = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1L);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PollResultPersistenceBusyException(
                    "Interrupted before retrying " + operation,
                    ex);
        }
    }
}
