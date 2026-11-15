package com.example.apktranslatorpro.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolkitCategory {
    CODE_AND_APK,
    UTILITIES
}

enum class ToolkitModule(
    val id: String,
    val titleTr: String,
    val subtitleTr: String,
    val category: ToolkitCategory,
    val icon: ImageVector
) {
    SIMPLE_EDIT("simple_edit", "Hızlı Düzenle", "App adı, metinler", ToolkitCategory.CODE_AND_APK, Icons.Filled.EditNote),
    FULL_EDIT("full_edit", "Tam Düzenleme", "ARSC & Önizleme", ToolkitCategory.CODE_AND_APK, Icons.Filled.DriveFileRenameOutline),
    APK_DECOMPILER("apk_decompiler", "APK Decompiler", "Apktool önbellek", ToolkitCategory.CODE_AND_APK, Icons.Filled.FolderCopy),
    APK_REBUILDER("apk_rebuilder", "APK Rebuilder", "Yeniden derle", ToolkitCategory.CODE_AND_APK, Icons.Filled.Build),
    XML_DIFF("xml_diff", "XML Karşılaştır", "Yan yana diff", ToolkitCategory.CODE_AND_APK, Icons.AutoMirrored.Filled.CompareArrows),
    APK_COMPARE("apk_compare", "APK Karşılaştır", "Sürüm & SHA fark", ToolkitCategory.CODE_AND_APK, Icons.Filled.Compare),
    GIT_INTEGRATION("git_clone", "Git & Git Clone", "HTTPS clone & push", ToolkitCategory.CODE_AND_APK, Icons.Filled.CloudSync),
    PROJECT_MANAGER("project_manager", "Proje & Sözlük", "Room & WorkManager", ToolkitCategory.CODE_AND_APK, Icons.Filled.FolderSpecial),
    SMALI_EDITOR("smali_editor", "Smali Editör", "Renkli bytecode", ToolkitCategory.CODE_AND_APK, Icons.Filled.Terminal),
    DEX_EDITOR("dex_editor", "DEX İnceleyici", "Sınıf & metotlar", ToolkitCategory.CODE_AND_APK, Icons.Filled.Memory),
    JAVA_VIEWER("java_viewer", "Java Görüntüleyici", "DEX → Java kodu", ToolkitCategory.CODE_AND_APK, Icons.Filled.DataObject),
    MANIFEST_EDITOR("manifest_editor", "Manifest Editör", "AndroidManifest", ToolkitCategory.CODE_AND_APK, Icons.Filled.Android),
    RESOURCE_EDITOR("resource_editor", "Kaynak Editör", "Anahtar & Diller", ToolkitCategory.CODE_AND_APK, Icons.Filled.Description),
    XML_EDITOR("xml_editor", "XML Editör", "strings & layout", ToolkitCategory.CODE_AND_APK, Icons.Filled.Code),
    STRING_CREATOR("string_creator", "String Oluşturucu", "Yeni XML kaynağı", ToolkitCategory.CODE_AND_APK, Icons.Filled.AddCircleOutline),
    PATCH_CREATOR("patch_creator", "Yama Oluşturucu", "Diff JSON export", ToolkitCategory.CODE_AND_APK, Icons.Filled.Difference),
    DEVELOPER_BROWSER("dev_browser", "Geliştirici Tarayıcı", "Önbellek ağacı", ToolkitCategory.UTILITIES, Icons.Filled.Archive),
    APK_SIGNER("apk_signer", "APK İmzalayıcı", "v1/v2/v3 & PKCS12", ToolkitCategory.UTILITIES, Icons.Filled.VerifiedUser),
    ZIP_MANAGER("zip_manager", "ZIP Yöneticisi", "Arşiv & dışa aktar", ToolkitCategory.UTILITIES, Icons.Filled.FolderZip),
    BACKUP_RESTORE("backup_restore", "Yedek & Geçmiş", "Room APK geçmişi", ToolkitCategory.UTILITIES, Icons.Filled.Backup),
    NOTIFICATION_MAKER("notification_maker", "Bildirim Üretici", "Sistem bildirimi", ToolkitCategory.UTILITIES, Icons.Filled.NotificationsActive),
    TOAST_MAKER("toast_maker", "Toast Üretici", "Anlık mesaj", ToolkitCategory.UTILITIES, Icons.Filled.Key),
    BASE64_TOOLS("base64_tools", "Base64 Araçları", "Kodla & çöz", ToolkitCategory.UTILITIES, Icons.Filled.Password),
    HTML_ENCODER("html_encoder", "HTML Kodlayıcı", "Escape & Unescape", ToolkitCategory.UTILITIES, Icons.Filled.Html)
}
