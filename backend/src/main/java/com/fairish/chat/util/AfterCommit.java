package com.fairish.chat.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect (such as a STOMP push) only after the surrounding
 * transaction commits.
 *
 * <p>Publishing before commit can expose a message that a recipient
 * acknowledges before it is readable, or resurrect state that is later rolled
 * back. When no transaction synchronization is active - for example in unit
 * tests or a read-only path - the action runs immediately.</p>
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
