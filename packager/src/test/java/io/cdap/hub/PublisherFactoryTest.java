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

import io.cdap.hub.publisher.GCSPublisher;
import io.cdap.hub.publisher.PublisherFactory;
import io.cdap.hub.publisher.S3Publisher;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PublisherFactoryTest {

    @Test
    public void getPublisher_publisherArgumentInvalid_systemErrorExit() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to useto publish packages : 's3' or 'gcs'."))
                .addOption(new Option("gcsP", "gcsProjectId", true, "GCP project id to target."))
                .addOption(new Option("gcsB", "gcsBucketName", true, "GCS bucket namewhere packages will be published."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "incorrect"};
        CommandLine commandLine = parser.parse(options, args);

        try {
            // WHEN
            new PublisherFactory().getPublisher(commandLine);
            Assertions.fail("Should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            // THEN
            Assertions.assertEquals(
                    "Must specify a publisher when publishing. Either 's3' or 'gcs'.",
                    exception.getMessage());
        }
    }

    @Test
    public void getPublisher_publisherIsGcsAndGcsProjectIdArgMissing_throwsException() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to useto publish packages : 's3' or 'gcs'."))
                .addOption(new Option("gcsP", "gcsProjectId", true, "GCP project id to target."))
                .addOption(new Option("gcsB", "gcsBucketName", true, "GCS bucket name where packages will be published."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "gcs"};
        CommandLine commandLine = parser.parse(options, args);

        try {
            // WHEN
            new PublisherFactory().getPublisher(commandLine);
            Assertions.fail("Should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            // THEN
            Assertions.assertEquals(
                    "Must specify a project id when publishing to GCS. Please use parameter 'gcsP'.",
                    exception.getMessage());
        }
    }

    @Test
    public void getPublisher_publisherIsGcsAndGcsBucketArgMissing_throwsException() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to useto publish packages : 's3' or 'gcs'."))
                .addOption(new Option("gcsP", "gcsProjectId", true, "GCP project id to target."))
                .addOption(new Option("gcsB", "gcsBucketName", true, "GCS bucket name where packages will be published."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "gcs", "-gcsP", "someProject"};
        CommandLine commandLine = parser.parse(options, args);

        try {
            // WHEN
            new PublisherFactory().getPublisher(commandLine);
            Assertions.fail("Should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            // THEN
            Assertions.assertEquals(
                    "Must specify a bucket name when publishing to GCS. Please use parameter 'gcsB'.",
                    exception.getMessage());
        }
    }

    @Test
    public void getPublisher_publisherIsGcsAndArgsValid_returnGCSPublisher() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to useto publish packages : 's3' or 'gcs'."))
                .addOption(new Option("gcsP", "gcsProjectId", true, "GCP project id to target."))
                .addOption(new Option("gcsB", "gcsBucketName", true, "GCS bucket name where packages will be published."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "gcs", "-gcsP", "someProject", "-gcsB", "someBucket"};
        CommandLine commandLine = parser.parse(options, args);

        // WHEN
        GCSPublisher publisher = (GCSPublisher) new PublisherFactory().getPublisher(commandLine);

        // THEN
        Assertions.assertNotNull(publisher);
    }

    @Test
    public void getPublisher_publisherIsS3AndS3BucketArgMissing_throwsException() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to use to publish packages : 's3' or 'gcs'."))
                .addOption(new Option("s3b", "s3bucket", true, "The S3 bucket to publish packages to."))
                .addOption(new Option("s3a", "s3access", true, "Access key to publish to s3."))
                .addOption(new Option("s3s", "s3secret", true, "Secret key to publish to s3."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "s3"};
        CommandLine commandLine = parser.parse(options, args);

        try {
            // WHEN
            new PublisherFactory().getPublisher(commandLine);
            Assertions.fail("Should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            // THEN
            Assertions.assertEquals(
                    "Must specify a bucket when publishing.",
                    exception.getMessage());
        }
    }

    @Test
    public void getPublisher_publisherIsS3AndS3AccessKeyArgMissing_throwsException() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to use to publish packages : 's3' or 'gcs'."))
                .addOption(new Option("s3b", "s3bucket", true, "The S3 bucket to publish packages to."))
                .addOption(new Option("s3a", "s3access", true, "Access key to publish to s3."))
                .addOption(new Option("s3s", "s3secret", true, "Secret key to publish to s3."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "s3", "-s3b", "someBucket"};
        CommandLine commandLine = parser.parse(options, args);

        try {
            // WHEN
            new PublisherFactory().getPublisher(commandLine);
            Assertions.fail("Should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            // THEN
            Assertions.assertEquals(
                    "Must specify an s3 access key when publishing.",
                    exception.getMessage());
        }
    }

    @Test
    public void getPublisher_publisherIsS3AndS3SecretArgMissing_throwsException() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to use to publish packages : 's3' or 'gcs'."))
                .addOption(new Option("s3b", "s3bucket", true, "The S3 bucket to publish packages to."))
                .addOption(new Option("s3a", "s3access", true, "Access key to publish to s3."))
                .addOption(new Option("s3s", "s3secret", true, "Secret key to publish to s3."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "s3", "-s3b", "someBucket", "-s3a", "someAccess"};
        CommandLine commandLine = parser.parse(options, args);

        try {
            // WHEN
            new PublisherFactory().getPublisher(commandLine);
            Assertions.fail("Should have thrown an IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            // THEN
            Assertions.assertEquals(
                    "Must specify an s3 secret key when publishing.",
                    exception.getMessage());
        }
    }

    @Test
    public void getPublisher_publisherIsS3AndArgsValid_returnS3Publisher() throws ParseException {
        // GIVEN
        Options options = new Options()
                .addOption(new Option("pub", "publisher", true, "The Publisher to use to publish packages : 's3' or 'gcs'."))
                .addOption(new Option("s3b", "s3bucket", true, "The S3 bucket to publish packages to."))
                .addOption(new Option("s3a", "s3access", true, "Access key to publish to s3."))
                .addOption(new Option("s3s", "s3secret", true, "Secret key to publish to s3."));

        CommandLineParser parser = new BasicParser();
        String[] args = new String[]{"-pub", "s3", "-s3b", "someBucket", "-s3a", "someAccess", "-s3s", "someSecret"};
        CommandLine commandLine = parser.parse(options, args);

        // WHEN
        S3Publisher publisher = (S3Publisher) new PublisherFactory().getPublisher(commandLine);

        // THEN
        Assertions.assertNotNull(publisher);
    }
}
