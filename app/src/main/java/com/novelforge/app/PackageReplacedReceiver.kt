package com.novelforge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 装完新版本自动把 App 拉起来，不用自己去桌面找 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val relaunch = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { context.startActivity(relaunch) }
        }
    }
}
