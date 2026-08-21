package com.ivi.audiobook.domain.usecase

import com.ivi.audiobook.data.library.LibraryScanResult
import com.ivi.audiobook.data.library.LibraryScanner
import javax.inject.Inject

class ScanLibrary @Inject constructor(
    private val scanner: LibraryScanner,
) {
    suspend operator fun invoke(forced: Boolean = false): LibraryScanResult = scanner.scan(forced)
}
