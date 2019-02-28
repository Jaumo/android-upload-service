package net.gotev.uploadservice

/**
 * Used to group together upload tasks in order to provide feedback and progress for a collection
 * of tasks.
 */
data class UploadTaskGroup(
        var id: String,
        var size: Int,
        var completed: Int,
        var remaining: Int,
        var cancelled: Int,
        var error: Int
)