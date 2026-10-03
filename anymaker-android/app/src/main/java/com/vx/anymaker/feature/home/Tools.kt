package com.vx.anymaker.feature.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.ui.graphics.vector.ImageVector
import com.vx.anymaker.R
import com.vx.anymaker.nav.Routes

enum class ToolSection(@StringRes val title: Int) {
    PHOTO(R.string.section_photo),
    SCAN(R.string.section_scan),
    PDF(R.string.section_pdf),
}

/**
 * One tile on the home screen. [route] is null until the tool ships; such tools show a
 * "Coming soon" badge so users see what the app will grow into.
 */
data class Tool(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val section: ToolSection,
    val route: String?,
    /** Extra search words in English; titles are matched in the user's language too. */
    val keywords: List<String> = emptyList(),
) {
    val available: Boolean get() = route != null
}

val allTools: List<Tool> = listOf(
    Tool("resize", R.string.tool_resize, R.string.tool_resize_desc, Icons.Outlined.PhotoSizeSelectLarge, ToolSection.PHOTO, Routes.RESIZE,
        listOf("compress", "kb", "shrink", "reduce", "size", "pixel", "photo", "image")),
    Tool("batch", R.string.tool_batch, R.string.tool_batch_desc, Icons.Outlined.Collections, ToolSection.PHOTO, null,
        listOf("bulk", "multiple", "many", "zip", "photo")),
    Tool("crop", R.string.tool_crop, R.string.tool_crop_desc, Icons.Outlined.Crop, ToolSection.PHOTO, null,
        listOf("rotate", "flip", "edit", "photo")),
    Tool("convert", R.string.tool_convert, R.string.tool_convert_desc, Icons.Outlined.Transform, ToolSection.PHOTO, null,
        listOf("jpg", "png", "webp", "heic", "format", "photo")),
    Tool("remove_bg", R.string.tool_remove_bg, R.string.tool_remove_bg_desc, Icons.Outlined.AutoFixHigh, ToolSection.PHOTO, null,
        listOf("background", "eraser", "cutout", "transparent", "photo")),
    Tool("passport", R.string.tool_passport, R.string.tool_passport_desc, Icons.Outlined.AccountBox, ToolSection.PHOTO, null,
        listOf("visa", "id", "2x2", "35x45", "photo")),
    Tool("doc_scan", R.string.tool_doc_scan, R.string.tool_doc_scan_desc, Icons.Outlined.DocumentScanner, ToolSection.SCAN, null,
        listOf("scanner", "camera", "paper", "document")),
    Tool("ocr", R.string.tool_ocr, R.string.tool_ocr_desc, Icons.Outlined.TextFields, ToolSection.SCAN, null,
        listOf("ocr", "text", "recognize", "copy", "extract")),
    Tool("qr_scan", R.string.tool_qr_scan, R.string.tool_qr_scan_desc, Icons.Outlined.QrCodeScanner, ToolSection.SCAN, null,
        listOf("qr", "barcode", "reader", "code")),
    Tool("qr_create", R.string.tool_qr_create, R.string.tool_qr_create_desc, Icons.Outlined.QrCode, ToolSection.SCAN, null,
        listOf("qr", "generator", "wifi", "code")),
    Tool("signature", R.string.tool_signature, R.string.tool_signature_desc, Icons.Outlined.Draw, ToolSection.SCAN, null,
        listOf("sign", "signature", "transparent")),
    Tool("images_to_pdf", R.string.tool_images_to_pdf, R.string.tool_images_to_pdf_desc, Icons.Outlined.PictureAsPdf, ToolSection.PDF, null,
        listOf("pdf", "jpg to pdf", "photo to pdf", "convert")),
    Tool("pdf_edit", R.string.tool_pdf_edit, R.string.tool_pdf_edit_desc, Icons.Outlined.Edit, ToolSection.PDF, null,
        listOf("pdf", "editor", "sign", "annotate", "text")),
    Tool("pdf_merge", R.string.tool_pdf_merge, R.string.tool_pdf_merge_desc, Icons.Outlined.Merge, ToolSection.PDF, null,
        listOf("pdf", "combine", "join")),
    Tool("pdf_split", R.string.tool_pdf_split, R.string.tool_pdf_split_desc, Icons.Outlined.ContentCut, ToolSection.PDF, null,
        listOf("pdf", "extract", "pages")),
    Tool("pdf_compress", R.string.tool_pdf_compress, R.string.tool_pdf_compress_desc, Icons.Outlined.Compress, ToolSection.PDF, null,
        listOf("pdf", "reduce", "smaller", "size")),
    Tool("pdf_lock", R.string.tool_pdf_lock, R.string.tool_pdf_lock_desc, Icons.Outlined.Lock, ToolSection.PDF, null,
        listOf("pdf", "password", "protect", "unlock")),
)

/** Tools whose localized title or English keywords contain [query], ignoring case. */
fun filterTools(tools: List<Tool>, query: String, titleOf: (Tool) -> String): List<Tool> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return tools
    return tools.filter { t ->
        titleOf(t).lowercase().contains(q) || t.keywords.any { it.contains(q) }
    }
}
