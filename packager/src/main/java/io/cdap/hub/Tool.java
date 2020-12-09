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
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import io.cdap.hub.publisher.Publisher;
import io.cdap.hub.publisher.PublisherFactory;
import io.cdap.hub.publisher.S3Publisher;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tool used to create, sign, and publish packages.
 */
public class Tool {
  private static final Logger LOG = LoggerFactory.getLogger(Tool.class);

  public static void main(String[] args) throws Exception {

    Options options = new Options()
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
      .addOption(new Option("pub", "publisher", true, "The Publisher to use to publish packages : 's3' or 'gcs'."))
      .addOption(new Option("s3b", "s3bucket", true, "The S3 bucket to publish packages to."))
      .addOption(new Option("s3p", "s3prefix", true,
                            "Optional prefix to use when publishing the s3. Defaults to empty."))
      .addOption(new Option("s3a", "s3access", true, "Access key to publish to s3."))
      .addOption(new Option("s3s", "s3secret", true, "Secret key to publish to s3."))
      .addOption(new Option("s3t", "s3timeout", true, "Timeout in seconds to use when pushing to s3. Defaults to 30."))
      .addOption(new Option("gcsP", "gcsProjectId", true, "GCP project id to target."))
      .addOption(new Option("gcsB", "gcsBucketName", true, "GCS bucket name where packages will be published."))
      .addOption(new Option("cfd", "cfdistribution", true, "Cloudfront distribution fronting the s3 bucket."))
      .addOption(new Option("cfa", "cfaccess", true, "Access key to invalidate cloudfront objects."))
      .addOption(new Option("cfs", "cfsecret", true, "Secret key to invalidate cloudfront objects."))
      .addOption(new Option("v", "version", true,
                            "Sets the version. Defaults to 'v2'. Note that it should not include slashes."));

    CommandLineParser parser = new BasicParser();
    CommandLine commandLine = parser.parse(options, args);
    String[] commandArgs = commandLine.getArgs();

    // if help is an option
    if (commandLine.hasOption("h") || commandArgs.length == 0) {
      HelpFormatter helpFormatter = new HelpFormatter();
      helpFormatter.printHelp(
        Packager.class.getName() + " command",
        "Supported commands are 'clean', 'build', and 'publish'.\n" +
          "'clean' will delete any existing archives and the packages.json catalog.\n" +
          "'build' will create package archives and the package.json catalog listing all packages found. " +
          "Expects packages to conform to a specific directory structure. " +
          "Each package should put its contents in the <base>/packages/<package-name>/<package-version> directory. " +
          "In that directory, there must be a spec.json file.\n" +
          "If the package contains a license, it must be named license.txt.\n" +
          "If the package contains an icon, it must be named icon.jpg.\n" +
          "Anything else in the package directory will be zipped up into a file named archive.zip.\n" +
          "'publish' will push the packages.json catalog, zips, and specs to s3.\n" +
          "'build' will always run a 'clean' first. 'publish' will always run a 'clean' and a 'build' first.",
        options, "");
      System.exit(0);
    }

    String command = commandArgs[0];
    if (!command.equalsIgnoreCase("build") &&
      !command.equalsIgnoreCase("clean") &&
      !command.equalsIgnoreCase("publish")) {
      LOG.error("Unrecognized command '{}'. Command must be 'clean', 'build', or 'publish'.", command);
      System.exit(1);
    }

    // read and validate options

    // get package directory
    String packageDirectoryStr = commandLine.hasOption("d") ?
      commandLine.getOptionValue("d") : System.getProperty("user.dir");

    File packageDirectory = new File(packageDirectoryStr);
    if (!packageDirectory.exists()) {
      LOG.error("Directory '{}' does not exist.", packageDirectory);
      System.exit(1);
    }
    if (!packageDirectory.isDirectory()) {
      LOG.error("Directory '{}' is not a directory.", packageDirectory);
      System.exit(1);
    }

    Signer signer = null;
    if (commandLine.hasOption('k')) {
      File keyFile = new File(commandLine.getOptionValue('k'));
      if (!keyFile.exists()) {
        LOG.error("Key file {} does not exist.", keyFile);
        System.exit(1);
      }
      if (!keyFile.isFile()) {
        LOG.error("Key file {} is not a file.", keyFile);
        System.exit(1);
      }

      if (!commandLine.hasOption('p')) {
        LOG.error("A password for the private key must be given.");
        System.exit(1);
      }
      String password = commandLine.getOptionValue('p');

      if (!commandLine.hasOption('i')) {
        LOG.error("The ID of the private key in the keyring must be given.");
        System.exit(1);
      }
      String keyIDHex = commandLine.getOptionValue('i');
      long keyID = 0;
      try {
        keyID = Longs.fromByteArray(BaseEncoding.base16().decode(keyIDHex.toUpperCase()));
      } catch (Exception e) {
        LOG.error("Could not decode {} into a long. Please ensure it is a long in hex format.", keyIDHex, e);
        System.exit(1);
      }
      signer = Signer.fromKeyFile(keyFile, keyID, password);
    }

    Set<String> whitelist = new HashSet<>();
    if (commandLine.hasOption('w')) {
      whitelist = parseWhitelist(commandLine.getOptionValue('w'));
    }
    Packager packager = new Packager(packageDirectory, signer, false, whitelist);
    Publisher publisher = command.equalsIgnoreCase("publish")
            ? new PublisherFactory().getPublisher(commandLine)
            : null;

    packager.clean();
    if (command.equalsIgnoreCase("clean")) {
      System.exit(0);
    }

    Hub hub = packager.build();
    if (command.equalsIgnoreCase("build")) {
      System.exit(0);
    }

    if (publisher == null) {
      System.exit(0);
    }

    publisher.publish(hub);
  }

  private static S3Publisher getPublisher(CommandLine commandLine, Set<String> whitelist) {

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
