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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Factory which creates the correct Publisher instance based on command line parameters : GCS or S3
 */
public class PublisherFactory {

    private static final Logger LOG = LoggerFactory.getLogger(Tool.class);

    public Publisher getPublisher(CommandLine commandLine) {

        if (Strings.isNullOrEmpty(commandLine.getOptionValue("publisher")) ||
                (!commandLine.getOptionValue("publisher").equals("s3") &&
                !commandLine.getOptionValue("publisher").equals("gcs"))) {
            LOG.error("Must specify a publisher when publishing. Either 's3' or 'gcs'.");
            System.exit(1);
        }

        if (commandLine.getOptionValue("publisher").equals("s3")) {
            return createS3Publisher(commandLine);
        } else {
            return createGCSPublisher(commandLine);
        }
    }

    private Publisher createGCSPublisher(CommandLine commandLine) {
        if (!commandLine.hasOption("gcsP") || !commandLine.hasOption("gcsB")) {
            LOG.error("Must specify a project id when publishing to GCS. Please use parameter 'gcsP'.");
            System.exit(1);
        }

        if (!commandLine.hasOption("gcsB")) {
            LOG.error("Must specify a bucket name when publishing to GCS. Please use parameter 'gcsB'.");
            System.exit(1);
        }

        return GCSPublisher
                .builder(commandLine.getOptionValue("gcsP"), commandLine.getOptionValue("gcsB"))
                .build();
    }

    private S3Publisher createS3Publisher(CommandLine commandLine) {

        if (!commandLine.hasOption("s3b")) {
            LOG.error("Must specify a bucket when publishing.");
            System.exit(1);
        }
        String bucket = commandLine.getOptionValue("s3b");

        if (!commandLine.hasOption("s3a")) {
            LOG.error("Must specify an s3 access key when publishing.");
            System.exit(1);
        }
        String s3AccessKey = commandLine.getOptionValue("s3a");

        if (!commandLine.hasOption("s3s")) {
            LOG.error("Must specify an s3 secret key when publishing.");
            System.exit(1);
        }
        String s3SecretKey = commandLine.getOptionValue("s3s");

        Set<String> whitelist = new HashSet<>();
        if (commandLine.hasOption('w')) {
            whitelist = parseWhitelist(commandLine.getOptionValue('w'));
        }

        S3Publisher.Builder builder = S3Publisher.builder(bucket, s3AccessKey, s3SecretKey)
                .setForcePush(commandLine.hasOption('f'))
                .setDryRun(commandLine.hasOption('y'))
                .setWhitelist(whitelist);

        // default to 'v2'
        String version = commandLine.hasOption("v") ? commandLine.getOptionValue("v") : "v2";

        if (commandLine.hasOption("s3p")) {
            String prefix = commandLine.getOptionValue("s3p");
            builder.setPrefix(prefix.endsWith("/") || prefix.isEmpty() ? prefix + version : prefix + "/" + version);
        } else {
            builder.setPrefix(version);
        }

        if (commandLine.hasOption("s3t")) {
            builder.setTimeout(Integer.parseInt(commandLine.getOptionValue("s3t")));
        }

        if (commandLine.hasOption("cfa")) {
            builder.setCloudfrontAccessKey(commandLine.getOptionValue("cfa"));
        }

        if (commandLine.hasOption("cfs")) {
            builder.setCloudfrontSecretKey(commandLine.getOptionValue("cfs"));
        }

        if (commandLine.hasOption("cfd")) {
            builder.setCloudfrontDistribution(commandLine.getOptionValue("cfd"));
        }

        return builder.build();
    }

    private static Set<String> parseWhitelist(String whitelistStr) {
        Set<String> whitelist = new LinkedHashSet<>();
        for (String packageStr : Splitter.on(',').trimResults().split(whitelistStr)) {
            whitelist.add(packageStr);
        }
        return whitelist;
    }
}
