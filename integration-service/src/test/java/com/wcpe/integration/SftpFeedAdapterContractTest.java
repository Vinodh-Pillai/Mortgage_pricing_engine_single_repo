package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class SftpFeedAdapterContractTest {
  @Test
  void sftpFixturePinsChecksumArchiveRefAndPathTraversalRejection() {
    IntegrationContractFixtureSupport.assertContains("sftp/sftp-feed-v1.json", "checksum", "archiveRef", "pathTraversalRejected");
    IntegrationContractFixtureSupport.assertContains("events/integration/sftp-file.schema.json", SftpFeedAdapterService.FILE_DISCOVERED_EVENT_TYPE, SftpFeedAdapterService.FILE_ARCHIVED_EVENT_TYPE, SftpFeedAdapterService.FILE_FAILED_EVENT_TYPE);
  }
}
