package com.sch.share.share

import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.widget.Toast
import com.sch.share.constant.WX_PACKAGE_NAME
import com.sch.share.constant.WX_SHARE_IMG_UI
import com.sch.share.manager.FileManager
import com.sch.share.manager.KEY_DISPLAY_NAME
import com.sch.share.utils.ClipboardUtil
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Created by StoneHui on 2019-11-28.
 * <p>
 * 分享给好友
 */
object SessionShare : BaseShare() {

    /**
     * 分享图片（[images]） 和文字（[text]）给好友
     */
    fun shareSession(activity: Activity, images: Array<Bitmap>, text: String) {
        if (!checkShareEnable(activity)) return
        thread(true) {
            // 扫描图片
            val uriList = mutableListOf<Uri>()
            images.forEach {
                val fileName = "$KEY_DISPLAY_NAME${System.currentTimeMillis()}.jpg"
                val resolver: ContentResolver = activity.contentResolver
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val filePath: String = FileManager.getTmpImageDir(activity) + File.separator + fileName
                    values.put(MediaStore.Images.Media.DATA, filePath)
                } else{
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "${File.separator}shareTmp")
                }
                val insertUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                var os: OutputStream? = null
                try {
                    if (insertUri != null) {
                        os = resolver.openOutputStream(insertUri)
                    }
                    if (os != null) {
                        it.compress(Bitmap.CompressFormat.JPEG, 100, os)
                        it.recycle()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    TimelineShare.closeIO(os)
                }
                insertUri?.let { uri -> uriList.add(uri) }
                if (uriList.size == images.size) {
                    // 扫描结束执行分享。
                    activity.runOnUiThread {
                        internalShareToSession(activity, uriList, text)
                    }
                }
            }
        }
    }

    private fun internalShareToSession(activity: Activity, fileList: List<Uri>, text: String = "") {
        if (!TextUtils.isEmpty(text)) {
            ClipboardUtil.setPrimaryClip(activity, "", text)
            Toast.makeText(activity, "请长按粘贴内容", Toast.LENGTH_LONG).show()
        }
        // 打开分享给好友界面
        val intent = Intent()
        intent.action = Intent.ACTION_SEND_MULTIPLE
        intent.component = ComponentName(WX_PACKAGE_NAME, WX_SHARE_IMG_UI)
        intent.type = "image/*"
        intent.putExtra("Kdescription", text)
        intent.putStringArrayListExtra(Intent.EXTRA_TEXT, arrayListOf())
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileList))
        activity.startActivity(intent)
    }
}