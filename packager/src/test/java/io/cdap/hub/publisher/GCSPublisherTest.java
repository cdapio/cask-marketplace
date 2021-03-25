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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static java.nio.charset.StandardCharsets.UTF_8;

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

  @Test
  public void shouldPush_ifPublishedAlreadyAndNoChange_fileShouldNotBePushedToBucket() throws Exception {
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
    // 1. Will upload all files to the bucket
    gcsPublisher.publish(hub);

    // 2. Asserting that spec.json has been uploaded
    Iterable<Blob> categoriesJson = mockedStorage
        .list("someBucket", Storage.BlobListOption.prefix(""))
        .getValues();
    assertTrue(
        StreamSupport.stream(categoriesJson.spliterator(), false)
            .map(BlobInfo::getName)
            .anyMatch(item -> item.equals("packages/test-service/1.0.0/spec.json"))
    );

    // 3. spec.json should not be uploaded again, as it already exists and hasn't changed since
    boolean shouldPush = gcsPublisher.shouldPush(
        Paths.get("packages/test-service/1.0.0"),
        new File("src/test/resources/packages/test-service/1.0.0/spec.json"));
    assertFalse(shouldPush);
  }

  @Test
  public void shouldPush_fileHasChanged_fileShouldBePushedToBucket() throws Exception {
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
    // 1. Publishing all content to storage bucket
    gcsPublisher.publish(hub);

    // 2. Asserting that spec.json has been uploaded
    Iterable<Blob> categoriesJson = mockedStorage
        .list("someBucket", Storage.BlobListOption.prefix(""))
        .getValues();
    assertTrue(
        StreamSupport.stream(categoriesJson.spliterator(), false)
            .map(BlobInfo::getName)
            .anyMatch(item -> item.equals("packages/test-service/1.0.0/spec.json"))
    );

    // 3. Reading the existing content of spec.json before making the change, in order to revert it at the end
    byte[] existingFileContent = Files.readAllBytes(
        Paths.get("src/test/resources/packages/test-service/1.0.0/spec.json"));

    // 4. Making the change to spec.json so it gets uploaded
    Files.write(
        Paths.get("src/test/resources/packages/test-service/1.0.0/spec.json"),
        System.lineSeparator().getBytes(UTF_8),
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND);

    // 5. spec.json should be uploaded again, as we changed it in step 4
    boolean shouldPush = gcsPublisher.shouldPush(
        Paths.get("packages/test-service/1.0.0"),
        new File("src/test/resources/packages/test-service/1.0.0/spec.json"));
    assertTrue(shouldPush);

    // 6. Reverting the change in spec.json by overwriting with previously existing content
    Files.write(
        Paths.get("src/test/resources/packages/test-service/1.0.0/spec.json"),
        existingFileContent);
  }
}
