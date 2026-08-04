package uz.bekobod.faolmfy.util

import uz.bekobod.faolmfy.data.TrackingConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Ish oynasi — 09:00–18:00 Asia/Tashkent, dushanba–juma (TZ F4).
 *
 * MUHIM: telefon vaqt zonasi o'zgartirilgan bo'lishi mumkin, shuning uchun
 * hisob-kitob DOIM Asia/Tashkent bo'yicha qilinadi, qurilma zonasi bo'yicha emas.
 */
object WorkWindow {

    val ZONE: ZoneId = ZoneId.of("Asia/Tashkent")

    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)

    fun today(): LocalDate = now().toLocalDate()

    private fun parse(hhmm: String): LocalTime {
        val parts = hhmm.split(":")
        return LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
    }

    fun startOf(day: LocalDate, cfg: TrackingConfig): ZonedDateTime =
        day.atTime(parse(cfg.workStart)).atZone(ZONE)

    fun endOf(day: LocalDate, cfg: TrackingConfig): ZonedDateTime =
        day.atTime(parse(cfg.workEnd)).atZone(ZONE)

    /**
     * Shu kun ish kunimi?
     * Serverdan olingan kalendar hafta kuni qoidasidan ustun turadi —
     * Vazirlar Mahkamasining kun ko'chirish qarori shu yerda ishlaydi.
     */
    fun isWorkday(
        day: LocalDate,
        cfg: TrackingConfig,
        holidays: Set<String>,
        extraWorkdays: Set<String>,
    ): Boolean {
        val iso = day.toString()
        if (extraWorkdays.contains(iso)) return true
        if (holidays.contains(iso)) return false
        return cfg.workDays.contains(day.dayOfWeek.value)
    }

    fun isInsideWindow(
        cfg: TrackingConfig,
        holidays: Set<String>,
        extraWorkdays: Set<String>,
        at: ZonedDateTime = now(),
    ): Boolean {
        val day = at.toLocalDate()
        if (!isWorkday(day, cfg, holidays, extraWorkdays)) return false
        return !at.isBefore(startOf(day, cfg)) && !at.isAfter(endOf(day, cfg))
    }

    /** Keyingi boshlanish vaqti (bugun hali boshlanmagan bo'lsa — bugun, aks holda keyingi ish kuni). */
    fun nextStart(
        cfg: TrackingConfig,
        holidays: Set<String>,
        extraWorkdays: Set<String>,
        from: ZonedDateTime = now(),
    ): ZonedDateTime {
        var day = from.toLocalDate()
        repeat(30) {
            if (isWorkday(day, cfg, holidays, extraWorkdays)) {
                val start = startOf(day, cfg)
                if (start.isAfter(from)) return start
            }
            day = day.plusDays(1)
        }
        return from.plusDays(1)
    }

    /** Bugungi tugash vaqti (agar bugun ish kuni bo'lsa). */
    fun todayEnd(
        cfg: TrackingConfig,
        holidays: Set<String>,
        extraWorkdays: Set<String>,
        from: ZonedDateTime = now(),
    ): ZonedDateTime? {
        val day = from.toLocalDate()
        if (!isWorkday(day, cfg, holidays, extraWorkdays)) return null
        val end = endOf(day, cfg)
        return if (end.isAfter(from)) end else null
    }
}
