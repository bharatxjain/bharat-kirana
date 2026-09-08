package com.kks.bharatkirana.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Convert a Supabase `created_at` timestamp ("2026-09-08T12:24:37.345678+00:00")
 * to a human-friendly local-time label ("Sep 8, 5:54 PM"). Falls back to
 * [fallback] if parsing fails — never crashes on a malformed timestamp.
 *
 * The parse handles Supabase's microsecond precision by trimming to ms and
 * normalising the timezone offset before the pattern reads it.
 */
fun formatOrderTimeLocal(createdAt: String, fallback: String = ""): String {
  if (createdAt.isBlank()) return fallback
  val millis = parseSupabaseIsoMillis(createdAt) ?: return fallback.ifBlank { createdAt }
  val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
  return fmt.format(Date(millis))
}

/**
 * Millisecond precision Instant from a Supabase timestamp, in the current JVM
 * timezone. Returns null when the input can't be parsed — Compose callers
 * should fall back to a stored display string in that case.
 */
internal fun parseSupabaseIsoMillis(createdAt: String): Long? {
  return runCatching {
    val normalized = createdAt.replace("Z", "+0000").let { s ->
      // Supabase returns 6-digit microseconds; SimpleDateFormat expects 3.
      if (s.length > 23 && s[19] == '.') {
        s.substring(0, 23) + s.substring(s.length - 6).replace(":", "")
      } else {
        s
      }
    }
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).parse(normalized)?.time
  }.getOrNull()
}
