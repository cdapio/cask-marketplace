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

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Helper to centralize command line options creation.
 */
public class CommandLineHelper {

  public static Options createCommandLineOptions() {
    return new Options()
        .addOption(new Option("h", "help", false, "Print this usage message."))
        .addOption(new Option("k", "key", true,
            "File containing the GPG secret keyring containing the private key to use to " +
                "sign package specs and archives. " +
                "If none is given, specs and archives will not be signed."))
        .addOption(new Option("p", "password", true,
            "Password for the GPG private key."))
        .addOption(new Option("i", "keyid", true,
            "Id (in hex) of the private key to use to sign specs and archives. " +
                "If you are using gpg, you can get this from 'gpg --list-keys --keyid-format LONG'"))
        .addOption(new Option("d", "dir", true,
            "Directory containing packages. Defaults to the current working directory."))
        .addOption(new Option("f", "force", false,
            "Push packages to S3 even if they have not changed. " +
                "This may be useful if the signatures have been updated, but the files have not."))
        .addOption(new Option("y", "dryrun", false,
            "Perform a dryrun, which won't actually publish to s3 or invalidate cloudfront objects."))
        .addOption(new Option("w", "whitelist", true,
            "A comma separated whitelist of categories to publish. Any package that does not have " +
                "one of these categories will not be published."))
        .addOption(new Option("pub", "publisher", true,
            "The Publisher to use to publish packages : 's3' or 'gcs'."))
        .addOption(new Option("s3b", "s3bucket", true, "The S3 bucket to publish packages to."))
        .addOption(new Option("s3p", "s3prefix", true,
            "Optional prefix to use when publishing the s3. Defaults to empty."))
        .addOption(new Option("s3a", "s3access", true, "Access key to publish to s3."))
        .addOption(new Option("s3s", "s3secret", true, "Secret key to publish to s3."))
        .addOption(new Option("s3t", "s3timeout", true,
            "Timeout in seconds to use when pushing to s3. Defaults to 30."))
        .addOption(new Option("gcsP", "gcsProjectId", true, "GCP project id to target."))
        .addOption(new Option("gcsB", "gcsBucketName", true,
            "GCS bucket name where packages will be published."))
        .addOption(new Option("cfd", "cfdistribution", true, "Cloudfront distribution fronting the s3 bucket."))
        .addOption(new Option("cfa", "cfaccess", true, "Access key to invalidate cloudfront objects."))
        .addOption(new Option("cfs", "cfsecret", true, "Secret key to invalidate cloudfront objects."))
        .addOption(new Option("v", "version", true,
            "Sets the version. Defaults to 'v2'. Note that it should not include slashes."));
  }
}
