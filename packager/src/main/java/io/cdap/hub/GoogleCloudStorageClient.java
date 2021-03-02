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

package io.cdap.hub;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * Component to create Google Cloud Storage connection. Can be used for mocking behaviour in tests.
 */
public class GoogleCloudStorageClient {

    public Storage createStorageConnection(String projectId) {
        return StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    }
}
