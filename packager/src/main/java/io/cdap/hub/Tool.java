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
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
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

    CommandLineParser parser = new BasicParser();
    Options commandLineOptions = CommandLineHelper.createCommandLineOptions();
    CommandLine commandLine = parser.parse(commandLineOptions, args);
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
        commandLineOptions, "");
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

  private static Set<String> parseWhitelist(String whitelistStr) {
    Set<String> whitelist = new LinkedHashSet<>();
    for (String packageStr : Splitter.on(',').trimResults().split(whitelistStr)) {
      whitelist.add(packageStr);
    }
    return whitelist;
  }
}
