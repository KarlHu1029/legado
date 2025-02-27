package io.legado.app.help.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.webdav.HttpAuth
import io.legado.app.lib.webdav.WebDav
import io.legado.app.model.NoStackTraceException
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AppWebDav {
    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"
    private val bookProgressUrl = "${rootWebDavUrl}bookProgress/"
    private val zipFilePath = "${appCtx.externalFiles.absolutePath}${File.separator}backup.zip"

    private val rootWebDavUrl: String
        get() {
            val configUrl = appCtx.getPrefString(PreferKey.webDavUrl)?.trim()
            var url = if (configUrl.isNullOrEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            AppConfig.webDavDir?.trim()?.let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    private val backupFileName: String
        get() {
            val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))
            return "backup${backupDate}.zip"
        }

    @Throws(Exception::class)
    suspend fun initWebDav(): Boolean {
        val account = appCtx.getPrefString(PreferKey.webDavAccount)
        val password = appCtx.getPrefString(PreferKey.webDavPassword)
        if (!account.isNullOrBlank() && !password.isNullOrBlank()) {
            HttpAuth.auth = HttpAuth.Auth(account, password)
            WebDav(rootWebDavUrl).makeAsDir()
            WebDav(bookProgressUrl).makeAsDir()
            return true
        }
        return false
    }

    @Throws(Exception::class)
    private suspend fun getWebDavFileNames(): ArrayList<String> {
        val url = rootWebDavUrl
        val names = arrayListOf<String>()
        if (initWebDav()) {
            var files = WebDav(url).listFiles()
            files = files.reversed()
            files.forEach {
                val name = it.displayName
                if (name?.startsWith("backup") == true) {
                    names.add(name)
                }
            }
        } else {
            throw NoStackTraceException("webDav没有配置")
        }
        return names
    }

    suspend fun showRestoreDialog(context: Context) {
        val names = withContext(IO) { getWebDavFileNames() }
        if (names.isNotEmpty()) {
            withContext(Main) {
                context.selector(
                    title = context.getString(R.string.select_restore_file),
                    items = names
                ) { _, index ->
                    if (index in 0 until names.size) {
                        Coroutine.async {
                            restoreWebDav(names[index])
                        }.onError {
                            appCtx.toastOnUi("WebDavError:${it.localizedMessage}")
                        }
                    }
                }
            }
        } else {
            throw NoStackTraceException("Web dav no back up file")
        }
    }

    private suspend fun restoreWebDav(name: String) {
        rootWebDavUrl.let {
            val webDav = WebDav(it + name)
            webDav.downloadTo(zipFilePath, true)
            @Suppress("BlockingMethodInNonBlockingContext")
            ZipUtils.unzipFile(zipFilePath, Backup.backupPath)
            Restore.restoreDatabase()
            Restore.restoreConfig()
        }
    }

    suspend fun hasBackUp(): Boolean {
        val url = "${rootWebDavUrl}${backupFileName}"
        return WebDav(url).exists()
    }

    suspend fun backUpWebDav(path: String) {
        try {
            if (initWebDav() && NetworkUtils.isAvailable()) {
                val paths = arrayListOf(*Backup.backupFileNames)
                for (i in 0 until paths.size) {
                    paths[i] = path + File.separator + paths[i]
                }
                FileUtils.delete(zipFilePath)
                if (ZipUtils.zipFiles(paths, zipFilePath)) {
                    val putUrl = "${rootWebDavUrl}${backupFileName}"
                    WebDav(putUrl).upload(zipFilePath)
                }
            }
        } catch (e: Exception) {
            appCtx.toastOnUi("WebDav\n${e.localizedMessage}")
        }
    }

    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        try {
            if (initWebDav() && NetworkUtils.isAvailable()) {
                // 默认导出到legado文件夹下exports目录
                val exportsWebDavUrl = rootWebDavUrl + EncoderUtils.escape("exports") + "/"
                // 在legado文件夹创建exports目录,如果不存在的话
                WebDav(exportsWebDavUrl).makeAsDir()
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl).upload(byteArray, "text/plain")
            }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                appCtx.toastOnUi("WebDav导出\n${e.localizedMessage}")
            }
        }
    }

    fun uploadBookProgress(book: Book) {
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        Coroutine.async {
            val bookProgress = BookProgress(book)
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(book)
            if (initWebDav()) {
                WebDav(url).upload(json.toByteArray(), "application/json")
            }
        }
    }

    suspend fun getBookProgress(book: Book): BookProgress? {
        if (initWebDav() && NetworkUtils.isAvailable()) {
            val url = getProgressUrl(book)
            WebDav(url).download()?.let { byteArray ->
                val json = String(byteArray)
                if (json.isJson()) {
                    return GSON.fromJsonObject<BookProgress>(json).getOrNull()
                }
            }
        }
        return null
    }

    private fun getProgressUrl(book: Book): String {
        return bookProgressUrl + book.name + "_" + book.author + ".json"
    }
}