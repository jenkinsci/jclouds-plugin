package org.jclouds.googlecomputeengine.domain;

import java.net.URI;
import java.util.Date;
import java.util.List;
import org.jclouds.googlecomputeengine.domain.AutoValue_Image;
import org.jclouds.googlecomputeengine.domain.AutoValue_Image_RawDisk;
import org.jclouds.googlecomputeengine.domain.Deprecated;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.json.SerializedNames;

public abstract class Image {
    public abstract String id();

    public abstract URI selfLink();

    public abstract Date creationTimestamp();

    public abstract String name();

    @Nullable
    public abstract String description();

    @Nullable
    public abstract String sourceType();

    @Nullable
    public abstract Image.RawDisk rawDisk();

    @Nullable
    public abstract Deprecated deprecated();

    public abstract Image.Status status();

    public abstract Long archiveSizeBytes();

    public abstract Long diskSizeGb();

    @Nullable
    public abstract String sourceDisk();

    @Nullable
    public abstract String sourceDiskId();

    @Nullable
    public abstract List<String> licenses();

    @SerializedNames({"id", "selfLink", "creationTimestamp", "name", "description", "sourceType", "rawDisk", "deprecated", "status", "archiveSizeBytes", "diskSizeGb", "sourceDisk", "sourceDiskId", "licenses"})
    public static Image create(String id, URI selfLink, Date creationTimestamp, String name, String description, String sourceType, Image.RawDisk rawDisk, Deprecated deprecated, Image.Status status, Long archiveSizeBytes, Long diskSizeGb, String sourceDisk, String sourceDiskId, List<String> licenses) {
        return new AutoValue_Image(id, selfLink, creationTimestamp, name, description, sourceType, rawDisk, deprecated, status, archiveSizeBytes, diskSizeGb, sourceDisk, sourceDiskId, licenses);
    }

    Image() {
    }

    public abstract static class RawDisk {
        public abstract URI source();

        public abstract String containerType();

        @Nullable
        public abstract String sha1Checksum();

        @SerializedNames({"source", "containerType", "sha1Checksum"})
        public static Image.RawDisk create(URI source, String containerType, String sha1Checksum) {
            return new AutoValue_Image_RawDisk(source, containerType, sha1Checksum);
        }

        RawDisk() {
        }
    }

    public static enum Status {
        FAILED,
        PENDING,
        READY;

        private Status() {
        }
    }
}
