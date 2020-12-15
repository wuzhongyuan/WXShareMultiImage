package com.sch.share.manager

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File


/**
 * Created by StoneHui on 2019-11-28.
 * <p>
 * 文件管理
 */
const val KEY_DISPLAY_NAME = "BeeGoAutoImage"
object FileManager {

    /**
     * 文件临时保存目录。
     */
    fun getTmpImageDir(context: Context): String {
        val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val child = "${File.separator}shareTmp"
        return File(parent, child)
                .run {
                    if (!exists()) {
                        mkdirs()
                    }
                    absolutePath
                }
    }

    /**
     * 清理临时文件。
     */
    fun clearTmpFile(context: Context) {
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val resolver = context.contentResolver
        val selection : String = MediaStore.Images.Media.DISPLAY_NAME + " like ?"
        val args: Array<String> = arrayOf("$KEY_DISPLAY_NAME%")

        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val cursor: Cursor? = resolver.query(external, projection, selection, args, null)
        var imageUri: Uri? = null
        if (cursor != null && cursor.moveToFirst()) {
            do {
                imageUri = ContentUris.withAppendedId(external, cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID)))
                resolver.delete(imageUri, null, null)
            } while (cursor.moveToNext())
            cursor.close()
        }
    }

    private fun pathToUri(context: Context, path: String?): Uri {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                Uri.fromFile(File(path))
            } else {
                FileProvider.getUriForFile(context, context.packageName + ".fileProvider", File(path))
            }
    }
}

