package io.legado.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.multidex.MultiDexApplication
import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.base.AppContextWrapper
import io.legado.app.constant.AppConst.channelIdDownload
import io.legado.app.constant.AppConst.channelIdReadAloud
import io.legado.app.constant.AppConst.channelIdWeb
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.CrashHandler
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.RuleBigDataHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig.applyDayNight
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.cronet.CronetLoader
import io.legado.app.model.BookCover
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import splitties.systemservices.notificationManager
import java.util.concurrent.TimeUnit

class App : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler(this)
        //预下载Cronet so
        CronetLoader.preDownload()
        createNotificationChannels()
        applyDayNight(this)
        LiveEventBus.config()
            .lifecycleObserverAlwaysActive(true)
            .autoClear(false)
        registerActivityLifecycleCallbacks(LifecycleHelp)
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(AppConfig)
        Coroutine.async {
            //初始化封面
            BookCover.toString()
            //清除过期数据
            appDb.cacheDao.clearDeadline(System.currentTimeMillis())
            if (getPrefBoolean(PreferKey.autoClearExpired, true)) {
                appDb.searchBookDao
                    .clearExpired(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
            }
            RuleBigDataHelp.clearInvalid()
            //初始化简繁转换引擎
            when (AppConfig.chineseConverterType) {
                1 -> ChineseUtils.t2s("初始化")
                2 -> ChineseUtils.s2t("初始化")
                else -> null
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES,
            Configuration.UI_MODE_NIGHT_NO -> applyDayNight(this)
        }
    }

    /**
     * 创建通知ID
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val downloadChannel = NotificationChannel(
            channelIdDownload,
            getString(R.string.action_download),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val readAloudChannel = NotificationChannel(
            channelIdReadAloud,
            getString(R.string.read_aloud),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val webChannel = NotificationChannel(
            channelIdWeb,
            getString(R.string.web_service),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        //向notification manager 提交channel
        notificationManager.createNotificationChannels(
            listOf(
                downloadChannel,
                readAloudChannel,
                webChannel
            )
        )
    }

}
