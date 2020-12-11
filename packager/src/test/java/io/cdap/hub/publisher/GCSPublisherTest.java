/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.hub.publisher;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import io.cdap.hub.GoogleCloudStorageClient;
import io.cdap.hub.Hub;
import io.cdap.hub.Packager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.File;
import java.util.HashSet;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GCSPublisherTest {

  @Mock
  private GoogleCloudStorageClient googleCloudStorageClient;

  @Test
  public void publish_testServiceArtifact_allFilesUploadedCorrectly() throws Exception {
    // GIVEN
    String someProjectId = "someProjectId";

    // Stubbing the GoogleCloudStorageClient to return a mock storage
    Storage mockedStorage = LocalStorageHelper.getOptions().getService();
    when(googleCloudStorageClient.createStorageConnection(someProjectId))
        .thenReturn(mockedStorage);

    // Creating the local test Hub structure based on local src/test/resources/packages
    File packagesDirectory = new File("src/test/resources");
    Packager packager = new Packager(packagesDirectory, null, false, new HashSet<>());
    packager.clean();
    Hub hub = packager.build();

    // WHEN
    // Creating the publisher with the stubbed GoogleCloudStorageClient
    GCSPublisher gcsPublisher = GCSPublisher
        .builder(googleCloudStorageClient, someProjectId, "someBucket")
        .build();
    gcsPublisher.publish(hub);

    // THEN
    // Asserting that test-service artifact has been uploaded to the local test bucket to correct path
    // Icon, spec, and the jar
    Iterable<Blob> testServiceJar = mockedStorage
        .list("someBucket", Storage.BlobListOption.prefix("packages/test-service"))
        .getValues();
    assertTrue(
        StreamSupport.stream(testServiceJar.spliterator(), false)
            .map(BlobInfo::getName)
            .allMatch(item -> item.equals("packages/test-service/1.0.0/test-service-1.0.0.jar")
                || item.equals("packages/test-service/1.0.0/icon.png")
                || item.equals("packages/test-service/1.0.0/spec.json"))
    );

    // Asserting that packages.json has been uploaded to correct path
    Iterable<Blob> packagesJson = mockedStorage
        .list("someBucket", Storage.BlobListOption.prefix(""))
        .getValues();
    assertTrue(
        StreamSupport.stream(packagesJson.spliterator(), false)
            .map(BlobInfo::getName)
            .anyMatch(item -> item.equals("packages.json"))
    );

    // Asserting that categories.json has been uploaded to correct path
    Iterable<Blob> categoriesJson = mockedStorage
        .list("someBucket", Storage.BlobListOption.prefix(""))
        .getValues();
    assertTrue(
        StreamSupport.stream(categoriesJson.spliterator(), false)
            .map(BlobInfo::getName)
            .anyMatch(item -> item.equals("categories.json"))
    );
  }
}
