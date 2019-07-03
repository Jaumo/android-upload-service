package net.gotev.uploadservice;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.ViewGroup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Base class to subclass when creating upload tasks. It contains the logic common to all the tasks,
 * such as notification management, status broadcast, retry logic and some utility methods.
 *
 * @author Aleksandar Gotev
 */
public abstract class UploadTask implements Runnable {

    private static final String LOG_TAG = UploadTask.class.getSimpleName();

    /**
     * Constant which indicates that the upload task has been completed successfully.
     */
    protected static final int TASK_COMPLETED_SUCCESSFULLY = 200;

    /**
     * Constant which indicates an empty response from the server.
     */
    protected static final byte[] EMPTY_RESPONSE = "".getBytes(Charset.forName("UTF-8"));

    /**
     * Reference to the upload service instance.
     */
    protected UploadService service;

    /**
     * Contains all the parameters set in {@link UploadRequest}.
     */
    protected UploadTaskParameters params = null;

    /**
     * Contains the absolute local path of the successfully uploaded files.
     */
    private final List<String> successfullyUploadedFiles = new ArrayList<>();

    /**
     * Flag indicating if the operation should continue or is cancelled. You should never
     * explicitly set this value in your subclasses, as it's written by the Upload Service
     * when you call {@link UploadService#stopUpload(String)}. If this value is false, you should
     * terminate your upload task as soon as possible, so be sure to check the status when
     * performing long running operations such as data transfer. As a rule of thumb, check this
     * value at every step of the upload protocol you are implementing, and after that each chunk
     * of data that has been successfully transferred.
     */
    protected boolean shouldContinue = true;

    private int notificationId;
    private long lastProgressNotificationTime;
    private NotificationManager notificationManager;
    private long notificationCreationTimeMillis;

//    @Nullable private Snackbar snackbar = null;
    @Nullable private Activity snackbarActivity = null;
    @Nullable private NotificationSnackbar notificationSnackbar = null;

    /**
     * Total bytes to transfer. You should initialize this value in the
     * {@link UploadTask#upload()} method of your subclasses, before starting the upload data
     * transfer.
     */
    protected long totalBytes;

    /**
     * Total transferred bytes. You should update this value in your subclasses when you upload
     * some data, and before calling {@link UploadTask#broadcastProgress(long, long)}
     */
    protected long uploadedBytes;

    /**
     * Start timestamp of this upload task.
     */
    private final long startTime;

    /**
     * Counter of the upload attempts that has been made;
     */
    private int attempts;

    /**
     * A decoded and resized bitmap of the large icon
     */
    private Bitmap largeIconBitmap;

    /**
     * The path of the last bitmap that was rendered for the large icon
     */
    private String lastLargeIconBitmapRenderedPath;

    /**
     * Implementation of the upload logic.
     *
     * @throws Exception if an error occurs
     */
    abstract protected void upload() throws Exception;

    /**
     * Implement in subclasses to be able to do something when the upload is successful.
     */
    protected void onSuccessfulUpload() {
    }

    public UploadTask() {
        startTime = new Date().getTime();
    }

    /**
     * Initializes the {@link UploadTask}.<br>
     * Override this method in subclasses to perform custom task initialization and to get the
     * custom parameters set in {@link UploadRequest#initializeIntent(Intent)} method.
     *
     * @param service Upload Service instance. You should use this reference as your context.
     * @param intent  intent sent to the service to start the upload
     * @throws IOException if an I/O exception occurs while initializing
     */
    protected void init(UploadService service, Intent intent) throws IOException {
        this.notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        this.params = intent.getParcelableExtra(UploadService.PARAM_TASK_PARAMETERS);
        this.service = service;

        if (params.notificationConfig != null) {
            createNotificationChannel(params.notificationConfig.getHighImportanceNotificationChannelId(), params.notificationConfig.getHighImportanceNotificationChannelName(), NotificationManagerCompat.IMPORTANCE_HIGH);
            createNotificationChannel(params.notificationConfig.getLowImportanceNotificationChannelId(), params.notificationConfig.getLowImportanceNotificationChannelName(), NotificationManagerCompat.IMPORTANCE_LOW);
        }
    }

    private void createNotificationChannel(String notificationChannelId, String channelName, int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationChannelId == null) {
                notificationChannelId = UploadService.NAMESPACE + channelName;
            }

            if (notificationManager.getNotificationChannel(notificationChannelId) == null) {
                NotificationChannel channel = new NotificationChannel(notificationChannelId, channelName, importance);
                if (!params.notificationConfig.isRingToneEnabled()) {
                    channel.setSound(null, null);
                }
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public final void run() {

        attempts = 0;

        int errorDelay = UploadService.INITIAL_RETRY_WAIT_TIME;

        while (attempts <= params.getMaxRetries() && shouldContinue) {
            attempts++;

            try {
                createNotification(new UploadInfo(params.id));
                upload();
                break;

            } catch (Exception exc) {
                if (!shouldContinue) {
                    break;
                } else if (attempts > params.getMaxRetries()) {
                    broadcastError(exc);
                } else {
                    Logger.error(LOG_TAG, "Error in uploadId " + params.id
                            + " on attempt " + attempts
                            + ". Waiting " + errorDelay / 1000 + "s before next attempt. ", exc);

                    long beforeSleepTs = System.currentTimeMillis();

                    while (shouldContinue && System.currentTimeMillis() < (beforeSleepTs + errorDelay)) {
                        try {
                            Thread.sleep(2000);
                        } catch (Throwable ignored) {
                        }
                    }

                    errorDelay *= UploadService.BACKOFF_MULTIPLIER;
                    if (errorDelay > UploadService.MAX_RETRY_WAIT_TIME) {
                        errorDelay = UploadService.MAX_RETRY_WAIT_TIME;
                    }
                }
            }
        }

        if (!shouldContinue) {
            broadcastCancelled();
        }
    }

    /**
     * Sets the last time the notification was updated.
     * This is handled automatically and you should never call this method.
     *
     * @param lastProgressNotificationTime time in milliseconds
     * @return {@link UploadTask}
     */
    protected final UploadTask setLastProgressNotificationTime(long lastProgressNotificationTime) {
        this.lastProgressNotificationTime = lastProgressNotificationTime;
        return this;
    }

    /**
     * Sets the upload notification ID for this task.
     * This gets called by {@link UploadService} when the task is initialized.
     * You should never call this method.
     *
     * @param notificationId notification ID
     * @return {@link UploadTask}
     */
    protected final UploadTask setNotificationId(int notificationId) {
        this.notificationId = notificationId;
        return this;
    }

    /**
     * Broadcasts a progress update.
     *
     * @param uploadedBytes number of bytes which has been uploaded to the server
     * @param totalBytes    total bytes of the request
     */
    protected final void broadcastProgress(final long uploadedBytes, final long totalBytes) {

        long currentTime = System.currentTimeMillis();
        if (uploadedBytes < totalBytes && currentTime < lastProgressNotificationTime + UploadService.PROGRESS_REPORT_INTERVAL) {
            return;
        }

        setLastProgressNotificationTime(currentTime);

        Logger.debug(LOG_TAG, "Broadcasting upload progress for " + params.id
                + ": " + uploadedBytes + " bytes of " + totalBytes);

        final UploadInfo uploadInfo = new UploadInfo(params.id, startTime, uploadedBytes,
                totalBytes, (attempts - 1),
                successfullyUploadedFiles,
                pathStringListFrom(params.files));

        BroadcastData data = new BroadcastData()
                .setStatus(BroadcastData.Status.IN_PROGRESS)
                .setUploadInfo(uploadInfo);

        final UploadStatusDelegate delegate = UploadService.getUploadStatusDelegate(params.id);
        if (delegate != null) {
            delegate.onProgress(service, uploadInfo);
        } else {
            service.sendBroadcast(data.getIntent());
        }

        updateNotificationProgress(uploadInfo);
    }

    /**
     * Broadcasts a completion status update and informs the {@link UploadService} that the task
     * executes successfully.
     * Call this when the task has completed the upload request and has received the response
     * from the server.
     *
     * @param response response got from the server
     */
    protected final void broadcastCompleted(final ServerResponse response) {

        final boolean successfulUpload = response.getHttpCode() >= 200 && response.getHttpCode() < 400;

        if (successfulUpload) {
            onSuccessfulUpload();

            if (params.autoDeleteSuccessfullyUploadedFiles && !successfullyUploadedFiles.isEmpty()) {
                for (String filePath : successfullyUploadedFiles) {
                    deleteFile(new File(filePath));
                }
            }
        }

        Logger.debug(LOG_TAG, "Broadcasting upload " + (successfulUpload ? "completed" : "error")
                + " for " + params.id);

        final UploadInfo uploadInfo = new UploadInfo(params.id, startTime, uploadedBytes,
                totalBytes, (attempts - 1),
                successfullyUploadedFiles,
                pathStringListFrom(params.files));

        final UploadNotificationConfig notificationConfig = params.notificationConfig;

        if (notificationConfig != null) {
            if (successfulUpload && notificationConfig.getCompleted().message != null) {
                updateNotification(uploadInfo, notificationConfig.getCompleted(), true);

            } else if (notificationConfig.getError().message != null) {
                updateNotification(uploadInfo, notificationConfig.getError(), true);
            }
        }

        final UploadStatusDelegate delegate = UploadService.getUploadStatusDelegate(params.id);
        if (delegate != null) {

            if (successfulUpload) {
                delegate.onCompleted(service, uploadInfo, response);
            } else {
                delegate.onError(service, uploadInfo, response, null);
            }
        } else {
            BroadcastData data = new BroadcastData()
                    .setStatus(successfulUpload ? BroadcastData.Status.COMPLETED : BroadcastData.Status.ERROR)
                    .setUploadInfo(uploadInfo)
                    .setServerResponse(response);

            service.sendBroadcast(data.getIntent());
        }

        cleanupResources();

        service.taskCompleted(params.id);
    }

    /**
     * Broadcast a cancelled status.
     * This called automatically by {@link UploadTask} when the user cancels the request,
     * and the specific implementation of {@link UploadTask#upload()} either
     * returns or throws an exception. You should never call this method explicitly in your
     * implementation.
     */
    protected final void broadcastCancelled() {

        Logger.debug(LOG_TAG, "Broadcasting cancellation for upload with ID: " + params.id);

        final UploadInfo uploadInfo = new UploadInfo(params.id, startTime, uploadedBytes,
                totalBytes, (attempts - 1),
                successfullyUploadedFiles,
                pathStringListFrom(params.files));

        final UploadNotificationConfig notificationConfig = params.notificationConfig;

        if (notificationConfig != null && notificationConfig.getCancelled().message != null) {
            updateNotification(uploadInfo, notificationConfig.getCancelled(), true);
        }

        BroadcastData data = new BroadcastData()
                .setStatus(BroadcastData.Status.CANCELLED)
                .setUploadInfo(uploadInfo);

        final UploadStatusDelegate delegate = UploadService.getUploadStatusDelegate(params.id);
        if (delegate != null) {
            delegate.onCancelled(service, uploadInfo);
        } else {
            service.sendBroadcast(data.getIntent());
        }

        cleanupResources();
        service.taskCompleted(params.id);
    }

    /**
     * Add a file to the list of the successfully uploaded files and remove it from the file list
     *
     * @param file file on the device
     */
    protected final void addSuccessfullyUploadedFile(UploadFile file) {
        if (!successfullyUploadedFiles.contains(file.path)) {
            successfullyUploadedFiles.add(file.path);
            params.files.remove(file);
        }
    }

    /**
     * Adds all the files to the list of successfully uploaded files.
     * This will automatically remove them from the params.getFiles() list.
     */
    protected final void addAllFilesToSuccessfullyUploadedFiles() {
        for (Iterator<UploadFile> iterator = params.files.iterator(); iterator.hasNext(); ) {
            UploadFile file = iterator.next();

            if (!successfullyUploadedFiles.contains(file.path)) {
                successfullyUploadedFiles.add(file.path);
            }
            iterator.remove();
        }
    }

    /**
     * Gets the list of all the successfully uploaded files.
     * You must not modify this list in your subclasses! You can only read its contents.
     * If you want to add an element into it,
     * use {@link UploadTask#addSuccessfullyUploadedFile(UploadFile)}
     *
     * @return list of strings
     */
    protected final List<String> getSuccessfullyUploadedFiles() {
        return successfullyUploadedFiles;
    }

    /**
     * Broadcasts an error.
     * This called automatically by {@link UploadTask} when the specific implementation of
     * {@link UploadTask#upload()} throws an exception and there aren't any left retries.
     * You should never call this method explicitly in your implementation.
     *
     * @param exception exception to broadcast. It's the one thrown by the specific implementation
     *                  of {@link UploadTask#upload()}
     */
    private void broadcastError(final Exception exception) {

        Logger.info(LOG_TAG, "Broadcasting error for upload with ID: "
                + params.id + ". " + exception.getMessage());

        final UploadInfo uploadInfo = new UploadInfo(params.id, startTime, uploadedBytes,
                totalBytes, (attempts - 1),
                successfullyUploadedFiles,
                pathStringListFrom(params.files));

        final UploadNotificationConfig notificationConfig = params.notificationConfig;

        if (notificationConfig != null && notificationConfig.getError().message != null) {
            updateNotification(uploadInfo, notificationConfig.getError(), false);
        }

        BroadcastData data = new BroadcastData()
                .setStatus(BroadcastData.Status.ERROR)
                .setUploadInfo(uploadInfo)
                .setException(exception);

        final UploadStatusDelegate delegate = UploadService.getUploadStatusDelegate(params.id);
        if (delegate != null) {
            delegate.onError(service, uploadInfo, null, exception);
        } else {
            service.sendBroadcast(data.getIntent());
        }

        service.taskCompleted(params.id);
    }

    /**
     * If the upload task is initialized with a notification configuration, this handles its
     * creation.
     *
     * @param uploadInfo upload information and statistics
     */
    private void createNotification(UploadInfo uploadInfo) {
        if (params.notificationConfig == null || params.notificationConfig.getProgress().message == null)
            return;

        UploadNotificationStatusConfig statusConfig = params.notificationConfig.getProgress();
        notificationCreationTimeMillis = System.currentTimeMillis();
        populateLargeIconBitmap(statusConfig.largeNotificationDimensions, uploadInfo.getCurrentFilePath());

        NotificationCompat.Builder notification = new NotificationCompat.Builder(service, params.notificationConfig.getHighImportanceNotificationChannelId())
                .setWhen(notificationCreationTimeMillis)
                .setContentTitle(getNotificationTitle(uploadInfo, statusConfig))
                .setContentText(getNotificationContent(uploadInfo, statusConfig))
                .setSmallIcon(statusConfig.iconResourceID)
                .setColor(statusConfig.iconColorInt)
                .setGroup(UploadService.NAMESPACE)
                .setProgress(100, 0, true)
                .setOngoing(true);

        if (largeIconBitmap != null && !largeIconBitmap.isRecycled()) {
            notification.setLargeIcon(largeIconBitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(largeIconBitmap)
                            .bigLargeIcon(null));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setContentIntent(statusConfig.getClickIntent(service));
        } else {
            // Creates a heads up notification (peek) on devices with OS < Oreo.
            notification.setFullScreenIntent(statusConfig.getClickIntent(service), true);
        }

        statusConfig.addActionsToNotificationBuilder(notification);

        Notification builtNotification = notification.build();

        if (service.holdForegroundNotification(params.id, builtNotification)) {
            notificationManager.cancel(notificationId);
        } else {
            notificationManager.notify(notificationId, builtNotification);
        }
    }

    /**
     * Informs the {@link UploadService} that the task has made some progress. You should call this
     * method from your task whenever you have successfully transferred some bytes to the server.
     *
     * @param uploadInfo upload information and statistics
     */
    private void updateNotificationProgress(UploadInfo uploadInfo) {
        if (params.notificationConfig == null || params.notificationConfig.getProgress().message == null)
            return;

        UploadNotificationStatusConfig statusConfig = params.notificationConfig.getProgress();
        populateLargeIconBitmap(statusConfig.largeNotificationDimensions, uploadInfo.getCurrentFilePath());

        String channelId = getChannelId(uploadInfo);
        int totalBytes = (int) uploadInfo.getTotalBytes();

        // This can happen at the start of an upload while size is being computed
        if (totalBytes < 1) {
            totalBytes = Integer.MAX_VALUE;
        }

        NotificationCompat.Builder notification = new NotificationCompat.Builder(service, channelId)
                .setWhen(notificationCreationTimeMillis)
                .setContentTitle(getNotificationTitle(uploadInfo, statusConfig))
                .setContentText(getNotificationContent(uploadInfo, statusConfig))
                .setContentIntent(statusConfig.getClickIntent(service))
                .setSmallIcon(statusConfig.iconResourceID)
                .setColor(statusConfig.iconColorInt)
                .setGroup(UploadService.NAMESPACE)
                .setProgress(totalBytes, (int) uploadInfo.getUploadedBytes(), false)
                .setOngoing(true);

        if (largeIconBitmap != null && !largeIconBitmap.isRecycled()) {
            notification.setLargeIcon(largeIconBitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(largeIconBitmap)
                            .bigLargeIcon(null));
        }

        statusConfig.addActionsToNotificationBuilder(notification);

        Notification builtNotification = notification.build();

        showSnackbar(uploadInfo, statusConfig, uploadInfo.getUploadedBytes(), totalBytes);

        if (service.holdForegroundNotification(params.id, builtNotification)) {
            notificationManager.cancel(notificationId);
        } else {
            notificationManager.notify(notificationId, builtNotification);
        }
    }

    private void setRingtone(NotificationCompat.Builder notification) {

        if (params.notificationConfig.isRingToneEnabled() && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Uri sound = RingtoneManager.getActualDefaultRingtoneUri(service, RingtoneManager.TYPE_NOTIFICATION);
            notification.setSound(sound);
        }

    }

    private void updateNotification(UploadInfo uploadInfo, UploadNotificationStatusConfig statusConfig, boolean isTerminal) {
        if (params.notificationConfig == null) return;

        notificationManager.cancel(notificationId);

        if (statusConfig.message == null) return;

        populateLargeIconBitmap(statusConfig.largeNotificationDimensions, uploadInfo.getCurrentFilePath());
        String channelId = getChannelId(uploadInfo);

        if (!statusConfig.autoClear) {
            NotificationCompat.Builder notification = new NotificationCompat.Builder(service, channelId)
                    .setContentTitle(getNotificationTitle(uploadInfo, statusConfig))
                    .setContentText(getNotificationContent(uploadInfo, statusConfig))
                    .setContentIntent(statusConfig.getClickIntent(service))
                    .setAutoCancel(statusConfig.clearOnAction)
                    .setSmallIcon(statusConfig.iconResourceID)
                    .setColor(statusConfig.iconColorInt)
                    .setGroup(UploadService.NAMESPACE)
                    .setProgress(0, 0, false)
                    .setOngoing(false);

            if (largeIconBitmap != null && !largeIconBitmap.isRecycled()) {
                notification.setLargeIcon(largeIconBitmap);
            }

            showSnackbar(uploadInfo, statusConfig, 0, 0);
            statusConfig.addActionsToNotificationBuilder(notification);

            setRingtone(notification);

            // this is needed because the main notification used to show progress is ongoing
            // and a new one has to be created to allow the user to dismiss it
            uploadInfo.setNotificationID(notificationId + 1);
            notificationManager.notify(notificationId + 1, notification.build());
        }
    }

    /**
     * Tries to delete a file from the device.
     * If it fails, the error will be printed in the LogCat.
     *
     * @param fileToDelete file to delete
     * @return true if the file has been deleted, otherwise false.
     */
    private boolean deleteFile(File fileToDelete) {
        boolean deleted = false;

        try {
            deleted = fileToDelete.delete();

            if (!deleted) {
                Logger.error(LOG_TAG, "Unable to delete: "
                        + fileToDelete.getAbsolutePath());
            } else {
                Logger.info(LOG_TAG, "Successfully deleted: "
                        + fileToDelete.getAbsolutePath());
            }

        } catch (Exception exc) {
            Logger.error(LOG_TAG,
                    "Error while deleting: " + fileToDelete.getAbsolutePath() +
                            " Check if you granted: android.permission.WRITE_EXTERNAL_STORAGE", exc);
        }

        return deleted;
    }

    private void populateLargeIconBitmap(Dimensions dimensions, String path) {
        if (largeIconBitmap == null || lastLargeIconBitmapRenderedPath.equalsIgnoreCase(path)) {
            lastLargeIconBitmapRenderedPath = path;
            largeIconBitmap = getLargeIconBitmap(dimensions, path);
        }
    }

    @Nullable
    private Bitmap getLargeIconBitmap(Dimensions dimensions, String path) {
        try {
            int targetWidth = Math.round(dimensions.getWidth());
            int targetHeight = Math.round(dimensions.getHeight());

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            int originalWidth = options.outWidth;
            int originalHeight = options.outHeight;

            if (targetWidth < 0) {
                targetWidth = originalWidth;
            }
            if (targetHeight < 0) {
                targetHeight = originalHeight;
            }

            int scaleFactor = Math.min(originalWidth / targetWidth, originalHeight / targetHeight);

            options.inJustDecodeBounds = false;
            options.inSampleSize = scaleFactor;
            options.inPurgeable = true;

            return BitmapFactory.decodeFile(path, options);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> pathStringListFrom(List<UploadFile> files) {
        final List<String> filesLeft = new ArrayList<>(files.size());
        for (UploadFile f : files) {
            filesLeft.add(f.getPath());
        }
        return filesLeft;
    }

    @CallSuper
    public void cancel() {
        this.shouldContinue = false;

        if (largeIconBitmap != null && !largeIconBitmap.isRecycled()) {
            largeIconBitmap.recycle();
        }
    }

    /**
     * We want to show the high importance channel at the start of an upload so the user is presented
     * with a peek notification. After the upload has started, we want to minimize the notification,
     * so we toggle it over to the low importance notification.
     *
     * @param uploadInfo
     * @return
     */
    private String getChannelId(UploadInfo uploadInfo) {
        if (uploadInfo.getSuccessfullyUploadedFiles().isEmpty() && uploadInfo.getTotalBytes() < 100) {
            return params.notificationConfig.getHighImportanceNotificationChannelId();
        } else {
            return params.notificationConfig.getLowImportanceNotificationChannelId();
        }
    }

    private void showSnackbar(UploadInfo uploadInfo, UploadNotificationStatusConfig statusConfig, long uploadedBytes, long totalBytes) {
        Activity activeActivity = ((CurrentActivityHolder) service.getApplication()).getCurrentActivity();

        if (activeActivity == null) {
            return;
        }

        String title = getNotificationTitle(uploadInfo, statusConfig);
        String message = getNotificationContent(uploadInfo, statusConfig);

        if (notificationSnackbar == null || snackbarActivity != activeActivity) {
            snackbarActivity = activeActivity;
            ViewGroup contentView = activeActivity.getWindow().getDecorView().findViewWithTag("content");
            ViewGroup snackbarView = contentView != null ? contentView : (ViewGroup) activeActivity.getWindow().getDecorView();

            notificationSnackbar = new NotificationSnackbar(snackbarActivity);
            notificationSnackbar.update(title, message, statusConfig, uploadedBytes, totalBytes, largeIconBitmap);
            notificationSnackbar.show(snackbarView);
            notificationSnackbar.setOnClickListener(v -> {
                try {
                    statusConfig.getClickIntent(snackbarActivity).send();
                    notificationSnackbar.hide();
                } catch (Exception e) {
                    Log.e("Pending Intent", e.getMessage(), e);
                }
            });
        } else {
            notificationSnackbar.update(title, message, statusConfig, uploadedBytes, totalBytes, largeIconBitmap);
        }
    }

    private void cleanupResources() {
        if (notificationSnackbar != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                notificationSnackbar.hide();
                notificationSnackbar = null;
                snackbarActivity = null;

                if (largeIconBitmap != null) {
                    largeIconBitmap.recycle();
                    largeIconBitmap = null;
                }
            }, 3500);
        }
        else {
            if (largeIconBitmap != null) {
                largeIconBitmap.recycle();
                largeIconBitmap = null;
            }

        }
    }

    private String getNotificationTitle(UploadInfo uploadInfo, UploadNotificationStatusConfig statusConfig) {
        return Placeholders.replace(statusConfig.title, uploadInfo, service.getIndexOfCurrentUploadTask(), service.getTotalTasks());
    }

    private String getNotificationContent(UploadInfo uploadInfo, UploadNotificationStatusConfig statusConfig) {
        return Placeholders.replace(statusConfig.message, uploadInfo, service.getIndexOfCurrentUploadTask(), service.getTotalTasks());
    }
}