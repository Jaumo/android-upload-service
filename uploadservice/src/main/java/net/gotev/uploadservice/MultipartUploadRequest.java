package net.gotev.uploadservice;

import android.content.Context;
import android.content.Intent;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;

/**
 * HTTP/Multipart upload request. This is the most common way to upload files on a server.
 * It's the same kind of request that browsers do when you use the &lt;form&gt; tag
 * with one or more files.
 *
 * @author gotev (Aleksandar Gotev)
 * @author eliasnaur
 *
 */
public class MultipartUploadRequest extends HttpUploadRequest<MultipartUploadRequest> {

    private static final String LOG_TAG = MultipartUploadRequest.class.getSimpleName();
    private boolean isUtf8Charset = false;

    /**
     * Creates a new multipart upload request.
     *
     * @param context application context
     * @param uploadId unique ID to assign to this upload request.<br>
     *                 It can be whatever string you want, as long as it's unique.
     *                 If you set it to null or an empty string, an UUID will be automatically
     *                 generated.<br> It's advised to keep a reference to it in your code,
     *                 so when you receive status updates in {@link UploadServiceBroadcastReceiver},
     *                 you know to which upload they refer to.
     * @param serverUrl URL of the server side script that will handle the multipart form upload.
     *                  E.g.: http://www.yourcompany.com/your/script
     * @throws IllegalArgumentException if one or more arguments are not valid
     * @throws MalformedURLException if the server URL is not valid
     */
    public MultipartUploadRequest(final Context context, final String uploadId, final String serverUrl)
        throws IllegalArgumentException, MalformedURLException {
        super(context, uploadId, serverUrl);
    }

    /**
     * Creates a new multipart upload request and automatically generates an upload id, that will
     * be returned when you call {@link UploadRequest#startUpload(ServiceParameters)}.
     *
     * @param context application context
     * @param serverUrl URL of the server side script that will handle the multipart form upload.
     *                  E.g.: http://www.yourcompany.com/your/script
     * @throws IllegalArgumentException if one or more arguments are not valid
     * @throws MalformedURLException if the server URL is not valid
     */
    public MultipartUploadRequest(final Context context, final String serverUrl)
        throws MalformedURLException, IllegalArgumentException {
        this(context, null, serverUrl);
    }

    @Override
    protected void initializeIntent(Intent intent) {
        super.initializeIntent(intent);
        intent.putExtra(MultipartUploadTask.PARAM_UTF8_CHARSET, isUtf8Charset);
    }

    @Override
    protected Class<? extends UploadTask> getTaskClass() {
        return MultipartUploadTask.class;
    }

    /**
     * Adds a file to this upload request.
     *
     * @param uploadFile file to upload
     * @throws FileNotFoundException if the file does not exist at the specified path
     * @throws IllegalArgumentException if one or more parameters are not valid
     * @return {@link MultipartUploadRequest}
     */
    public MultipartUploadRequest addFileToUpload(UploadFile uploadFile)
            throws FileNotFoundException, IllegalArgumentException {
        String filePath = uploadFile.getPath();

        if (uploadFile.getParameterName() == null || "".equals(uploadFile.getParameterName())) {
            throw new IllegalArgumentException("Please specify parameterName value for file: "
                                               + filePath);
        }

        uploadFile.setProperty(MultipartUploadTask.PROPERTY_PARAM_NAME, uploadFile.getParameterName());

        if (uploadFile.getContentType() == null || uploadFile.getContentType().isEmpty()) {
            uploadFile.setContentType(uploadFile.getResolvedContentType(context));
            Logger.debug(LOG_TAG, "Auto-detected MIME type for " + filePath
                    + " is: " + uploadFile.getContentType());
        } else {
            Logger.debug(LOG_TAG, "Content Type set for " + filePath
                    + " is: " + uploadFile.getContentType());
        }

        uploadFile.setProperty(MultipartUploadTask.PROPERTY_CONTENT_TYPE, uploadFile.getContentType());

        if (uploadFile.getFileName() == null || "".equals(uploadFile.getFileName())) {
            uploadFile.setFileName(uploadFile.getName(context));
            Logger.debug(LOG_TAG, "Using original file name: " + uploadFile.getFileName());
        } else {
            Logger.debug(LOG_TAG, "Using custom file name: " + uploadFile.getFileName());
        }

        uploadFile.setProperty(MultipartUploadTask.PROPERTY_REMOTE_FILE_NAME, uploadFile.getFileName());

        params.files.add(uploadFile);
        return this;
    }

    /**
     * Sets the charset for this multipart request to UTF-8. If not set, the standard US-ASCII
     * charset will be used.
     * @return request instance
     */
    public MultipartUploadRequest setUtf8Charset() {
        isUtf8Charset = true;
        return this;
    }
}
