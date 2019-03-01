package net.gotev.uploadservice;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import net.gotev.uploadservice.schemehandlers.SchemeHandler;
import net.gotev.uploadservice.schemehandlers.SchemeHandlerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedHashMap;

/**
 * Represents a file to upload.
 *
 * @author cankov
 * @author gotev (Aleksandar Gotev)
 */
public class UploadFile implements Parcelable {
    public static class Builder {
        private String path;
        private String parameterName;
        private String fileName;
        private String contentType;

        public static Builder newInstance(String path) {
            return new Builder(path);
        }

        private Builder(String path) {
            this.path = path;
        }

        /**
         * Name of the form parameter that will contain file's data.
         */
        public Builder setParameterName(String parameterName) {
            this.parameterName = parameterName;
            return this;
        }

        /**
         * File name seen by the server side script. If null, the original file name  will be used.
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Content type of the file. You can use constants defined in
         * {@link ContentType} class. Set this to null or empty string to try to
         * automatically detect the mime type from the file. If the mime type can't
         * be detected, {@code application/octet-stream} will be used by default.
         * @throws FileNotFoundException if the file does not exist at the specified path
         */
        public Builder setContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public UploadFile build() {
            return new UploadFile(this);
        }
    }


    protected final String path;
    private final String parameterName;
    private String fileName;
    private String contentType;
    private LinkedHashMap<String, String> properties = new LinkedHashMap<>();
    protected final SchemeHandler handler;


    /**
     * Creates a new UploadFile.
     *
     * @param builder
     * @throws FileNotFoundException if the file can't be found at the specified path
     * @throws IllegalArgumentException if you passed invalid argument values
     */

    public UploadFile(Builder builder) {
        if (builder.path == null || "".equals(builder.path)) {
            throw new IllegalArgumentException("Please specify a file path!");
        }

        if (!SchemeHandlerFactory.getInstance().isSupported(builder.path))
            throw new UnsupportedOperationException("Unsupported scheme: " + builder.path);

        this.path = builder.path;
        this.parameterName = builder.parameterName;
        this.fileName = builder.fileName;
        this.contentType = builder.contentType;

        try {
            this.handler = SchemeHandlerFactory.getInstance().get(path);
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Gets the file length in bytes.
     * @param context service context
     * @return file length
     */
    public long length(Context context) {
        return handler.getLength(context);
    }

    /**
     * Gets the {@link InputStream} to read the content of this file.
     * @param context service context
     * @return file input stream
     * @throws FileNotFoundException if the file can't be found at the path specified in the
     * constructor
     */
    public final InputStream getStream(Context context) throws FileNotFoundException {
        return handler.getInputStream(context);
    }

    /**
     * Returns the content type for the file
     * @param context service context
     * @return content type
     */
    public final String getResolvedContentType(Context context) {
        return handler.getContentType(context);
    }

    /**
     * Returns the name of this file.
     * @param context service context
     * @return string
     */
    public final String getName(Context context) {
        return handler.getName(context);
    }

    /**
     * Returns the string this was initialized with,
     * either an absolute file path or Android content URI
     * @return String
     */
    public final String getPath() {
        return this.path;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    // This is used to regenerate the object.
    // All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<UploadFile> CREATOR =
            new Parcelable.Creator<UploadFile>() {
        @Override
        public UploadFile createFromParcel(final Parcel in) {
            return new UploadFile(in);
        }

        @Override
        public UploadFile[] newArray(final int size) {
            return new UploadFile[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int arg1) {
        parcel.writeString(path);
        parcel.writeString(parameterName != null ? parameterName : "");
        parcel.writeString(fileName != null ? fileName : "");
        parcel.writeString(contentType != null ? contentType : "");
        parcel.writeSerializable(properties);
    }

    @SuppressWarnings("unchecked")
    private UploadFile(Parcel in) {
        this.path = in.readString();
        this.parameterName = in.readString();
        this.fileName = in.readString();
        this.contentType = in.readString();
        this.properties = (LinkedHashMap<String, String>) in.readSerializable();

        try {
            this.handler = SchemeHandlerFactory.getInstance().get(path);
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Sets a property for this file.
     * If you want to store objects, serialize them in JSON strings.
     * @param key property key
     * @param value property value
     */
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    /**
     * Gets a property associated to this file.
     * @param key property key
     * @return property value or null if the value does not exist.
     */
    public String getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Gets a property associated to this file.
     * @param key property key
     * @param defaultValue default value to use if the key does not exist or the value is null
     * @return property value or the default value passed
     */
    public String getProperty(String key, String defaultValue) {
        String val = properties.get(key);

        if (val == null) {
            val = defaultValue;
        }

        return val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UploadFile)) return false;

        UploadFile that = (UploadFile) o;

        return path.equals(that.path);

    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
