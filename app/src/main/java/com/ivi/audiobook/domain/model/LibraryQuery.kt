package com.ivi.audiobook.domain.model

enum class SortOrder {
    TITLE,
    AUTHOR,
    RECENTLY_ADDED,
    RECENTLY_PLAYED,
}

enum class SourceFilter {
    ALL,
    INTERNAL,
    USB,
}

data class LibraryQuery(
    val searchText: String = "",
    val sortOrder: SortOrder = SortOrder.TITLE,
    val source: SourceFilter = SourceFilter.ALL,
    val hideFinished: Boolean = false,
)
