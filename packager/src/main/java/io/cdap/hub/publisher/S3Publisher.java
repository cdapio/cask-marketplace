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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClient;
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.Paths;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.google.common.net.MediaType;
import io.cdap.hub.Hub;
import io.cdap.hub.Package;
import io.cdap.hub.SignedFile;
import io.cdap.hub.spec.CategoryMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import javax.annotation.Nullable;

/**
 * Publish packages to S3.
 */
public class S3Publisher implements Publisher {

  private static final Logger LOG = LoggerFactory.getLogger(S3Publisher.class);
  private static final FileTypeMap fileTypeMap = MimetypesFileTypeMap.getDefaultFileTypeMap();
  private final AmazonS3Client s3Client;
  @Nullable
  private final AmazonCloudFrontClient cfClient;
  private final String bucket;
  private final String prefix;
  @Nullable
  private final String cfDistribution;
  private final boolean forcePush;
  private final boolean dryrun;
  private final Set<String> whitelist;
  private final Set<String> updatedKeys;

  private S3Publisher(
      AmazonS3Client s3Client, @Nullable AmazonCloudFrontClient cfClient,
      String bucket, String prefix, @Nullable String cfDistribution,
      boolean forcePush, boolean dryrun, Set<String> whitelist) {
    this.s3Client = s3Client;
    this.cfClient = cfClient;
    this.bucket = bucket;
    this.prefix = prefix;
    this.cfDistribution = cfDistribution;
    this.forcePush = forcePush;
    this.dryrun = dryrun;
    this.whitelist = whitelist;
    this.updatedKeys = new HashSet<>();
  }

  @Override
  public void publish(Hub hub) throws Exception {
    updatedKeys.clear();
    List<Package> packages = hub.getPackages();
    for (Package pkg : packages) {
      publishPackage(pkg);
    }
    for (CategoryMeta categoryMeta : hub.getCategories()) {
      publishCategory(categoryMeta);
    }
    LOG.info("Publishing package catalog");
    putFilesIfChanged(prefix + "/", hub.getPackageCatalog());
    LOG.info("Publishing category catalog");
    putFilesIfChanged(prefix + "/", hub.getCategoryCatalog());

    if (cfClient != null && !updatedKeys.isEmpty()) {
      CreateInvalidationRequest invalidationRequest = new CreateInvalidationRequest()
          .withDistributionId(cfDistribution)
          .withInvalidationBatch(
              new InvalidationBatch()
                  .withPaths(new Paths().withItems(updatedKeys).withQuantity(updatedKeys.size()))
                  .withCallerReference(String.valueOf(System.currentTimeMillis())));
      if (!dryrun) {
        LOG.info("Invalidating cloudfront objects {}", updatedKeys);
        cfClient.createInvalidation(invalidationRequest);
      } else {
        LOG.info("dryrun - would have invalidated cloudfront objects {}", updatedKeys);
      }
    }
  }

  private void publishPackage(Package pkg) throws Exception {
    LOG.info("Publishing package {}-{}", pkg.getName(), pkg.getVersion());
    String keyPrefix = String.format("%s/packages/%s/%s/", prefix, pkg.getName(), pkg.getVersion());

    putFilesIfChanged(keyPrefix, pkg.getIcon());
    putFilesIfChanged(keyPrefix, pkg.getLicense());
    putFilesIfChanged(keyPrefix, pkg.getSpec().getFile(), pkg.getSpec().getSignature());
    if (pkg.getArchive() != null) {
      putFilesIfChanged(keyPrefix, pkg.getArchive().getFile(), pkg.getArchive().getSignature());
    }
    for (SignedFile file : pkg.getFiles()) {
      putFilesIfChanged(keyPrefix, file.getFile(), file.getSignature());
    }
    for (S3ObjectSummary objectSummary : s3Client.listObjects(bucket, keyPrefix).getObjectSummaries()) {
      String objectKey = objectSummary.getKey();
      String name = objectKey.substring(keyPrefix.length());
      if (!pkg.getFileNames().contains(name)) {
        if (!dryrun) {
          LOG.info("Deleting object {} from s3 since it does not exist in the package anymore.", objectKey);
          s3Client.deleteObject(bucket, objectKey);
        } else {
          LOG.info("dryrun - would have deleted {} from s3 since it does not exist in the package anymore.", objectKey);
        }
      }
    }
  }

  private void publishCategory(CategoryMeta categoryMeta) throws Exception {
    String keyPrefix = String.format("%s/categories/%s/", prefix, categoryMeta.getName());
    putFilesIfChanged(keyPrefix, categoryMeta.getIcon());
  }

  // if the specified file has changed, put it plus all extra files on s3.
  private void putFilesIfChanged(String keyPrefix, @Nullable File file, File... extraFiles) throws IOException {
    if (file != null && shouldPush(keyPrefix, file)) {
      putFile(keyPrefix, file);
      for (File extraFile : extraFiles) {
        if (extraFile != null) {
          putFile(keyPrefix, extraFile);
        }
      }
    }
  }

  // check if the file on s3 has a different md5 or the file length.
  private boolean shouldPush(String keyPrefix, File file) throws IOException {
    if (forcePush) {
      return true;
    }
    String key = keyPrefix + file.getName();
    if (s3Client.doesObjectExist(bucket, key)) {
      ObjectMetadata existingMeta = s3Client.getObjectMetadata(bucket, key);
      long fileLength = file.length();
      String md5Hex = BaseEncoding.base16().encode(Files.hash(file, Hashing.md5()).asBytes());
      if (existingMeta != null &&
          existingMeta.getContentLength() == fileLength &&
          existingMeta.getETag() != null && existingMeta.getETag().equalsIgnoreCase(md5Hex)) {
        LOG.info("{} has not changed, skipping upload to S3.", file);
        return false;
      }
    }
    return true;
  }

  private void putFile(String keyPrefix, File file) throws IOException {
    String ext = Files.getFileExtension(file.getName());
    String contentType;
    switch (ext) {
      case "json":
        contentType = MediaType.JSON_UTF_8.withoutParameters().toString();
        break;
      case "txt":
        contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString();
        break;
      case "png":
        contentType = MediaType.PNG.withoutParameters().toString();
        break;
      case "asc":
        contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString();
        break;
      default:
        contentType = fileTypeMap.getContentType(file);
    }
    ObjectMetadata newMeta = new ObjectMetadata();
    newMeta.setContentType(contentType);
    String key = keyPrefix + file.getName();
    PutObjectRequest request = new PutObjectRequest(bucket, key, file)
        .withCannedAcl(CannedAccessControlList.PublicRead)
        .withMetadata(newMeta);
    if (!dryrun) {
      LOG.info("put file {} into s3 with key {}", file, key);
      s3Client.putObject(request);
    } else {
      LOG.info("dryrun - would have put file {} into s3 with key {}", file, key);
    }
    updatedKeys.add("/" + key);
  }

  public static Builder builder(String s3Bucket, String s3AccessKey, String s3SecretKey) {
    return new Builder(s3Bucket, s3AccessKey, s3SecretKey);
  }

  /**
   * Builder to create the S3Publisher.
   */
  public static class Builder {

    private final String s3Bucket;
    private final String s3AccessKey;
    private final String s3SecretKey;
    private String prefix;
    private String cfDistribution;
    private String cfAccessKey;
    private String cfSecretKey;
    private boolean forcePush;
    private boolean dryrun;
    private int timeout;
    private Set<String> whitelist;

    public Builder(String s3Bucket, String s3AccessKey, String s3SecretKey) {
      this.s3Bucket = s3Bucket;
      this.s3AccessKey = s3AccessKey;
      this.s3SecretKey = s3SecretKey;
      forcePush = false;
      dryrun = false;
      timeout = 30;
      prefix = "";
      whitelist = new HashSet<>();
    }

    public Builder setCloudfrontDistribution(String distribution) {
      this.cfDistribution = distribution;
      return this;
    }

    public Builder setCloudfrontAccessKey(String accessKey) {
      this.cfAccessKey = accessKey;
      return this;
    }

    public Builder setCloudfrontSecretKey(String secretKey) {
      this.cfSecretKey = secretKey;
      return this;
    }

    public Builder setPrefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public Builder setForcePush(boolean forcePush) {
      this.forcePush = forcePush;
      return this;
    }

    public Builder setDryRun(boolean dryrun) {
      this.dryrun = dryrun;
      return this;
    }

    public Builder setTimeout(int timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder setWhitelist(Set<String> whitelist) {
      this.whitelist = whitelist;
      return this;
    }

    public S3Publisher build() {
      ClientConfiguration clientConf = new ClientConfiguration()
          .withProtocol(Protocol.HTTPS)
          .withSocketTimeout(timeout * 1000);

      AmazonS3Client s3Client = new AmazonS3Client(new BasicAWSCredentials(s3AccessKey, s3SecretKey), clientConf);

      AmazonCloudFrontClient cfClient = null;
      if (cfDistribution != null) {
        if (cfAccessKey == null || cfSecretKey == null) {
          throw new IllegalArgumentException(
              "When specifying a cloudfront distribution, must also specify a cloudfront access key and secret key.");
        }
        cfClient = new AmazonCloudFrontClient(new BasicAWSCredentials(cfAccessKey, cfSecretKey), clientConf);
      }

      return new S3Publisher(s3Client, cfClient, s3Bucket, prefix, cfDistribution, forcePush, dryrun, whitelist);
    }
  }
}
