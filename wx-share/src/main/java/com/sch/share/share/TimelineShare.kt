package com.sch.share.share

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.widget.Toast
import com.sch.share.Options
import com.sch.share.R
import com.sch.share.ShareInfo
import com.sch.share.WXShareMultiImageHelper
import com.sch.share.constant.WX_LAUNCHER_UI
import com.sch.share.constant.WX_PACKAGE_NAME
import com.sch.share.constant.WX_SHARE_TO_TIMELINE_UI
import com.sch.share.manager.FileManager
import com.sch.share.manager.KEY_DISPLAY_NAME
import com.sch.share.service.ServiceManager
import com.sch.share.utils.ClipboardUtil
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Created by StoneHui on 2019-11-28.
 * <p>
 * 分享到朋友圈
 */
object TimelineShare : BaseShare() {

    /**
     * 分享图片 [images] 到朋友圈。
     * 使用 [options] 设置是否自动填充文案、是否自动发布、回调函数等配置。
     */
    fun share(activity: Activity, images: Array<Bitmap>, options: Options) {
        activity.runOnUiThread {
            if (!checkShareEnable(activity)) return@runOnUiThread
            WXShareMultiImageHelper.clearTmpFile(activity)
            startShare(
                    activity,
                    images,
                    options
            )
        }
    }

    /**
     * 分享到微信朋友圈。
     *
     * @param activity [Context]
     * @param fileList 图片列表。
     * @param options [Options] 可选项。
     */
    private fun startShare(activity: Activity, fileList: Array<Bitmap>, options: Options) {
        if (!options.isAutoFill || WXShareMultiImageHelper.isServiceEnabled(activity)) {
            internalShare(activity, fileList, options)
            return
        }
        AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(activity.getString(R.string.wx_share_dialog_title))
                .setMessage(activity.getString(R.string.wx_share_dialog_message))
                .setPositiveButton(activity.getString(R.string.wx_share_dialog_positive_button_text)) { dialog, _ ->
                    dialog.cancel()
                    ServiceManager.openService(activity) {
                        options.isAutoFill = it
                        internalShare(activity, fileList, options)
                    }
                }
                .setNegativeButton(activity.getString(R.string.wx_share_dialog_negative_button_text)) { dialog, _ ->
                    dialog.cancel()
                    options.isAutoFill = false
                    internalShare(activity, fileList, options)
                }
                .show()
    }

    private fun internalShare(activity: Activity, fileList: Array<Bitmap>, options: Options) {
        var dialog: ProgressDialog? = null
        if (options.needShowLoading) {
            dialog = ProgressDialog(activity).apply {
                setCancelable(false)
                setMessage("请稍候...")
                show()
            }
        }
        thread(true) {
            // 扫描图片
            val uriList = mutableListOf<Uri>()
            fileList.forEach {
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
                    closeIO(os)
                }
                insertUri?.let { uri -> uriList.add(uri) }
                if (uriList.size == fileList.size) {
                    // 扫描结束执行分享。
                    activity.runOnUiThread {
                        dialog?.cancel()
                        options.onPrepareOpenWXListener?.invoke()
                        if (options.isAutoFill) {
                            shareToTimelineUIAuto(activity, options, uriList.reversed())
                        } else {
                            shareToTimelineUIManual(activity, options)
                        }
                    }
                }
            }
        }
    }

    fun closeIO(os: OutputStream?) {
        try {
            os?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // 分享到微信朋友圈（自动模式）。
    private fun shareToTimelineUIAuto(context: Context, options: Options, uriList: List<Uri>) {
        if (!TextUtils.isEmpty(options.text)) {
            ClipboardUtil.setPrimaryClip(context, "", options.text)
        }
        ShareInfo.options = options
        ShareInfo.setImageCount(1, uriList.size - 1)
        // 打开分享到朋友圈界面
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.component = ComponentName(WX_PACKAGE_NAME, WX_SHARE_TO_TIMELINE_UI)
        intent.type = "image/*"
        /*
         * 新版微信会把 Kdescription 参数显示到分享输入框中。
         * 为兼容新旧版本，这里不传文本了，统一使用剪切板粘贴。
         * intent.putExtra("Kdescription", options.text)
         */
        intent.putExtra(Intent.EXTRA_STREAM, uriList[0])
        (context as Activity).startActivity(intent)
    }

    // 分享到微信朋友圈（手动模式）。
    private fun shareToTimelineUIManual(context: Context, options: Options) {
        if (!TextUtils.isEmpty(options.text)) {
            ClipboardUtil.setPrimaryClip(context, "", options.text)
            Toast.makeText(context, "请手动选择图片，长按粘贴内容！", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "请手动选择图片！", Toast.LENGTH_LONG).show()
        }
        // 打开微信
        val intent = Intent(Intent.ACTION_MAIN)
        intent.action = Intent.ACTION_MAIN
        intent.component = ComponentName(WX_PACKAGE_NAME, WX_LAUNCHER_UI)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

}