package com.wrongbook.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

    fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun todayEndMillis(): Long = todayStartMillis() + 24L * 60 * 60 * 1000

    fun relativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
            else -> formatDate(timestamp)
        }
    }
}
