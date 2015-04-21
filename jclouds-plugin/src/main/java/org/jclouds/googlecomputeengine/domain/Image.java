/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.googlecomputeengine.domain;

import static shaded.com.google.common.base.Objects.equal;
import static shaded.com.google.common.base.Objects.toStringHelper;
import static shaded.com.google.common.base.Optional.fromNullable;
import static shaded.com.google.common.base.Preconditions.checkNotNull;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.util.Date;

import shaded.com.google.common.annotations.Beta;
import shaded.com.google.common.base.Objects;
import static shaded.com.google.common.base.Objects.equal;
import static shaded.com.google.common.base.Objects.toStringHelper;
import shaded.com.google.common.base.Optional;
import static shaded.com.google.common.base.Optional.fromNullable;
import static shaded.com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a disk image to use on an instance.
 *
 * @see <a href="https://developers.google.com/compute/docs/reference/v1/images"/>
 */
@Beta
public final class Image extends Resource {

   private final String sourceType;
   private final RawDisk rawDisk;
   private final Optional<Deprecated> deprecated;

   @ConstructorProperties({
           "id", "creationTimestamp", "selfLink", "name", "description", "sourceType",
           "rawDisk", "deprecated"
   })
   protected Image(String id, Date creationTimestamp, URI selfLink, String name, String description,
                   String sourceType, RawDisk rawDisk, Deprecated deprecated) {
      super(Resource.Kind.IMAGE, id, creationTimestamp, selfLink, name, description);
      this.sourceType = checkNotNull(sourceType, "sourceType of %s", name);
      this.rawDisk = rawDisk;
      this.deprecated = fromNullable(deprecated);
   }

   /**
    * @return must be RAW; provided by the client when the disk image is created.
    */
   public String getSourceType() {
      return sourceType;
   }

   /**
    * @return the raw disk image parameters.
    */
   public RawDisk getRawDisk() {
      return rawDisk;
   }

   /**
    * @return the deprecation information for this image
    */
   public Optional<Deprecated> getDeprecated() {
      return deprecated;
   }

   /**
    * {@inheritDoc}
    */
   protected Objects.ToStringHelper string() {
      return super.string()
              .omitNullValues()
              .add("sourceType", sourceType)
              .add("rawDisk", rawDisk)
              .add("deprecated", deprecated.orNull());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String toString() {
      return string().toString();
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return new Builder().fromImage(this);
   }

   public static final class Builder extends Resource.Builder<Builder> {

      private String sourceType;
      private RawDisk rawDisk;
      private Deprecated deprecated;

      /**
       * @see Image#getSourceType()
       */
      public Builder sourceType(String sourceType) {
         this.sourceType = checkNotNull(sourceType, "sourceType");
         return this;
      }

      /**
       * @see Image#getDeprecated()
       */
      public Builder deprecated(Deprecated deprecated) {
         this.deprecated = checkNotNull(deprecated, "deprecated");
         return this;
      }

      /**
       * @see Image#getRawDisk()
       */
      public Builder rawDisk(RawDisk rawDisk) {
         this.rawDisk = checkNotNull(rawDisk);
         return this;
      }

      @Override
      protected Builder self() {
         return this;
      }

      public Image build() {
         return new Image(super.id, super.creationTimestamp, super.selfLink, super.name,
                 super.description, sourceType, rawDisk, deprecated);
      }

      public Builder fromImage(Image in) {
         return super.fromResource(in)
                 .sourceType(in.getSourceType())
                 .rawDisk(in.getRawDisk())
                 .deprecated(in.getDeprecated().orNull());
      }

   }

   /**
    * A raw disk image, usually the base for an image.
    *
    * @see <a href="https://developers.google.com/compute/docs/reference/v1/images"/>
    */
   public static class RawDisk {

      private final String source;
      private final String containerType;
      private final Optional<String> sha1Checksum;

      @ConstructorProperties({
              "source", "containerType", "sha1Checksum"
      })
      private RawDisk(String source, String containerType, String sha1Checksum) {
         this.source = checkNotNull(source, "source");
         this.containerType = checkNotNull(containerType, "containerType");
         this.sha1Checksum = fromNullable(sha1Checksum);
      }

      /**
       * @return the full Google Cloud Storage URL where the disk image is stored; provided by the client when the disk
       *         image is created.
       */
      public String getSource() {
         return source;
      }

      /**
       * @return the format used to encode and transmit the block device.
       */
      public String getContainerType() {
         return containerType;
      }

      /**
       * @return an optional SHA1 checksum of the disk image before unpackaging; provided by the client when the disk
       *         image is created.
       */
      public Optional<String> getSha1Checksum() {
         return sha1Checksum;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int hashCode() {
         return Objects.hashCode(source, containerType, sha1Checksum);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (obj == null || getClass() != obj.getClass()) return false;
         RawDisk that = RawDisk.class.cast(obj);
         return equal(this.source, that.source)
                 && equal(this.containerType, that.containerType)
                 && equal(this.sha1Checksum, that.sha1Checksum);
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this)
                 .omitNullValues()
                 .add("source", source)
                 .add("containerType", containerType)
                 .add("sha1Checksum", sha1Checksum.orNull());
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
         return string().toString();
      }

      public static Builder builder() {
         return new Builder();
      }

      public Builder toBuilder() {
         return builder().fromImageRawDisk(this);
      }

      public static class Builder {

         private String source;
         private String containerType;
         private String sha1Checksum;

         /**
          * @see org.jclouds.googlecomputeengine.domain.Image.RawDisk#getSource()
          */
         public Builder source(String source) {
            this.source = checkNotNull(source);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Image.RawDisk#getContainerType()
          */
         public Builder containerType(String containerType) {
            this.containerType = checkNotNull(containerType);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Image.RawDisk#getSha1Checksum()
          */
         public Builder sha1Checksum(String sha1Checksum) {
            this.sha1Checksum = sha1Checksum;
            return this;
         }

         public RawDisk build() {
            return new RawDisk(source, containerType, sha1Checksum);
         }

         public Builder fromImageRawDisk(RawDisk rawDisk) {
            return new Builder().source(rawDisk.getSource())
                    .containerType(rawDisk.getContainerType())
                    .sha1Checksum(rawDisk.getSha1Checksum().orNull());
         }
      }
   }
}
