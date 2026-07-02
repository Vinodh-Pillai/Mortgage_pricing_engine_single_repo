package com.wcpe.lock.nonqm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.lock.LockServiceException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class FailClosedNonQmLockRepositoryTest {
  @Test
  void failClosedRepositoryRejectsNonQmStateWhenDurableRepositoryIsNotWired() {
    FailClosedNonQmLockRepository repository = new FailClosedNonQmLockRepository();

    LockServiceException exception = assertThrows(
      LockServiceException.class,
      () -> repository.findLock(UUID.fromString("10000000-0000-0000-0000-000000000001"), "NONQM-LOCK-1")
    );

    assertEquals("PERSISTENCE_NOT_DURABLE", exception.code());
    assertTrue(exception.getMessage().contains("production Non-QM lock state cannot use process-local storage"));
  }
}
