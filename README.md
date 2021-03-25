# CDAP Hub

## Setup

Since this repo stores jar files, it is required that you use git-lfs.
See https://git-lfs.github.com/ for information on installing and settting up git-lfs.

## Directory Structure

The Hub repository requires this directory structure:

    packages/<name>/<version>/spec.json
    packages/<name>/<version>/icon.jpg
    packages/<name>/<version>/license.txt
    packages/<name>/<version>/<other files>

Anything that falls under 'other files' will be zipped up by the packager into an 'archive.zip' file.

## Local Hub Setup

Read the [LOCALSETUP](LOCALSETUP.md) guide to setup CDAP Hub locally on your machine

## Packager

To build the packager:

    cd packager
    mvn clean package
    cd ..

The packager can be used to create the packages.json catalog file, create 'archive.zip'
files, sign package specs and archives, and push the package files to s3. You can see the
help manual by running:

    java -cp packager/target/*:packager/target/lib/* io.cdap.hub.Tool

The packager uses PGP to sign archives and specs. It is therefore compatible with keyrings
created with GnuPG. For example:

    java -cp packager/target/*:packager/target/lib/* io.cdap.hub.Tool build -k ~/.gnupg/secring.gpg -i 499BC990789824FD -p mypassword

This will go through the files under the 'packages' directory, adding a 'packages.json'
catalog at the top level and adding 'spec.json.asc', 'archive.zip', and 'archive.zip.asc' files:

    packages.json
    packages/<name>/<version>/spec.json
    packages/<name>/<version>/spec.json.asc
    packages/<name>/<version>/icon.jpg
    packages/<name>/<version>/license.txt
    packages/<name>/<version>/<file1>
    packages/<name>/<version>/<file1>.asc
    packages/<name>/<version>/<file2>
    packages/<name>/<version>/<file2>.asc

## Publisher

You can also build all the packages and `publish` them, to either AWS s3 bucket or GCS bucket.<br>
This will build and sign all packages, as well as push anything that has changed to the target you've chosen.
The tool will use the md5 and file size to determine whether an object has changed or not.
Signatures will only be pushed if the corresponding file has changed.

### To publish to AWS s3

    java -cp packager/target/*:packager/target/lib/* io.cdap.hub.Tool publish -publisher s3 -k <gpg keyring file> -i <keyid> -p <key password> -s3b <s3 bucket> -s3a <s3 access key> -s3s <s3 secret key>

### To publish to GCS bucket

    java -cp "packager/target/*":"packager/target/lib/*" io.cdap.hub.Tool publish -publisher gcs -gcsP <your gcp project id> -gcsB <your bucket>

## Package specs

The spec.json file must be a JSON Object with this format:


    {
      "specVersion": "1.0",
      "label": "<label>",
      "description": "<description>",
      "author": "<author>",
      "org": "<org>",
      "created": <timestamp in seconds>,
      "categories": [ <categories> ],
      "cdapVersion": "<cdap version range>" (for example: "[4.0.0,4.1.0)")
      "actions": [
        {
          "type": "create_stream" | "create_app" | "create_artifact" | "create_dataset" | "load_datapack",
          "arguments": [
            {
              "name": "<arg name>",
              "value": <arg value>
            },
            ...
          ]
        },
        ...
      ]
    }


## Generator

The generator is a tool used to generate new packages from existing packages. It is useful if almost
everything about the package is the same except for the CDAP version and plugin version (for pipelines).

To generate new packages:

    java -cp packager/target/*:packager/target/lib/* io.cdap.hub.Generator -cv <cdap-version> -gv <plugins-version> -pv <package-version> -bv <base-version> generate

For example:

    java -cp packager/target/*:packager/target/lib/* io.cdap.hub.Generator -cv 4.1.0-SNAPSHOT -gv 1.6.0-SNAPSHOT -pv 1.1.0 -bv 1.0.1 generate

will generate new 1.1.0 packages from existing 1.0.1 packages. The `cdapVersion` of the new packages will be `4.1.0-SNAPSHOT`,
and the artifact version for plugins in pipeline configs will be `1.6.0-SNAPSHOT`. 

By default, the tool will ignore any beta packages and will only create new packages for those with category 'usecase' or
'pipeline'. To do this for different categories, use the '-c' option. To include beta packages, use the '-b' option.

The generator can also modify existing packages instead of creating new packages. For example:

    java -cp packager/target/*:packager/target/lib/* io.cdap.hub.Generator -cv 4.1.0 -gv 1.6.0 -pv 1.1.0 modify

will modify all 1.1.0 packages to use 4.1.0 as the `cdapVersion` and 1.6.0 as the `plugin version`.

