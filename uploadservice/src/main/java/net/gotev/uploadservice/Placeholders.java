package net.gotev.uploadservice;

/**
 * Contains all the placeholders that is possible to use in the notification text strings.
 *
 * @author Aleksandar Gotev
 */
public class Placeholders {

    /**
     * Placeholder to display the total elapsed upload time in minutes and seconds.
     * E.g.: 34s, 4m 33s, 45m 21s
     */
    public static final String ELAPSED_TIME = "[[ELAPSED_TIME]]";

    /**
     * Placeholder to display the average upload rate. E.g.: 6 Mbit/s, 634 Kbit/s, 232 bit/s
     */
    public static final String UPLOAD_RATE = "[[UPLOAD_RATE]]";

    /**
     * Placeholder to display the integer progress percent from 0 to 100. E.g.: 75%
     */
    public static final String PROGRESS = "[[PROGRESS]]";

    /**
     * Placeholder to display the number of successfully uploaded files.
     * Bear in mind that in case of HTTP/Multipart or Binary uploads which does not support
     * resume, if the request gets restarted due to an error, the number of uploaded files will
     * be reset to zero.
     */
    public static final String UPLOADED_FILES = "[[UPLOADED_FILES]]";

    /**
     * The current index of the file being uploaded
     */
    public static final String CURRENT_FILE_INDEX = "[[CURRENT_FILE_INDEX]]";

    /**
     * Placeholder to display the total number of files to upload.
     */
    public static final String TOTAL_FILES = "[[TOTAL_FILES]]";

    /**
     * Replace placeholders in a string.
     *
     * @param string     string in which to replace placeholders
     * @param uploadInfo upload information data
     * @return string with replaced placeholders
     */
    public static String replace(String string, UploadInfo uploadInfo) {
        if (string == null || string.isEmpty())
            return "";

        String tmp;

        int successfullyUploadedFiles = uploadInfo.getSuccessfullyUploadedFiles().size();
        int totalFiles = uploadInfo.getTotalFiles();
        int currentFileIndex = successfullyUploadedFiles + 1 < totalFiles ? successfullyUploadedFiles + 1 : totalFiles;

        tmp = string.replace(ELAPSED_TIME, uploadInfo.getElapsedTimeString());
        tmp = tmp.replace(PROGRESS, uploadInfo.getProgressPercent() + "%");
        tmp = tmp.replace(UPLOAD_RATE, uploadInfo.getUploadRateString());
        tmp = tmp.replace(UPLOADED_FILES, Integer.toString(successfullyUploadedFiles));
        tmp = tmp.replace(CURRENT_FILE_INDEX, Integer.toString(currentFileIndex));
        tmp = tmp.replace(TOTAL_FILES, totalFiles > 0 ? Integer.toString(totalFiles) : "1");

        return tmp;
    }
}
