package com.wcpe.observability.cache;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReferenceDataSource {
  List<ReferenceDataRecord> findVersions(UUID tenantId, ReferenceDataset dataset, LocalDate asOfDate);
}
