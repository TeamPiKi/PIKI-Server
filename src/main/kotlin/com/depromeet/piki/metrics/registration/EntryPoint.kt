package com.depromeet.piki.metrics.registration

enum class EntryPoint {
    EXTERNAL_SHARE,
    IN_APP,
    UNKNOWN,
    ;

    companion object {
        const val HEADER = "X-Client-Entry-Point"

        fun from(raw: String?): EntryPoint {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
