package com.appy.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Result sealed class for APK processing operations
 */
sealed class ApkProcessingResult {
    data class Progress(val progress: Float, val message: String) : ApkProcessingResult()
    data class ReadyToSave(val tempFilePath: String, val suggestedFileName: String) : ApkProcessingResult()
    data class Error(val message: String) : ApkProcessingResult()
}

/**
 * ApkProcessor - Repository/Service class for APK generation
 *
 * Implements "Binary Template Modification" strategy:
 * 1. Opens pre-compiled base-web-template.apk from assets
 * 2. Modifies assets/config.json with user's URL
 * 3. Injects custom icon if provided
 * 4. Signs the modified APK using bundled keystore with apksig library
 */
class ApkProcessor(private val context: Context) {

    companion object {
        private const val TEMPLATE_APK = "base-web-template.apk"
        private const val CONFIG_FILE = "assets/config.json"
        private const val MANIFEST_FILE = "AndroidManifest.xml"
        private const val RESOURCES_FILE = "resources.arsc"
        private const val KEYSTORE_FILE = "debug.p12"
        private const val KEYSTORE_PASSWORD = "android"
        private const val KEY_ALIAS = "androiddebugkey"
        
        // Template package ID - must match the applicationId in template/build.gradle.kts
        private const val TEMPLATE_PACKAGE_ID = "com.appy.generated.webapp.placeholder.app"
        
        // Template app name - must match the app_name string in template/res/values/strings.xml
        private const val TEMPLATE_APP_NAME = "AppyGeneratedWebApplicationPlaceholderNameHere"
    }

    /**
     * Generates an APK from the template with the specified configuration
     * Emits progress updates as Flow, ending with ReadyToSave containing temp file path
     */
    fun generateApk(
        url: String,
        appName: String = "WebApp",
        packageId: String = "com.webapp.app",
        iconUri: Uri? = null,
        statusBarDark: Boolean = false,
        enableOfflineCache: Boolean = false
    ): Flow<ApkProcessingResult> = flow {
        try {
            emit(ApkProcessingResult.Progress(0.1f, "Preparing template..."))

            // Step 1: Copy template APK from assets to cache
            val templateFile = copyTemplateToCache()
            emit(ApkProcessingResult.Progress(0.2f, "Template extracted"))

            // Step 2: Create output in cache directory (temporary)
            val sanitizedName = sanitizeFileName(appName)
            val outputFile = File(context.cacheDir, "${sanitizedName}_${System.currentTimeMillis()}.apk")

            emit(ApkProcessingResult.Progress(0.3f, "Modifying configuration..."))

            // Step 3: Modify the APK (inject config.json)
            modifyApk(templateFile, outputFile, url, appName, packageId, statusBarDark, enableOfflineCache)
            emit(ApkProcessingResult.Progress(0.5f, "Configuration injected"))

            // Step 4: Inject custom icon if provided
            if (iconUri != null) {
                emit(ApkProcessingResult.Progress(0.6f, "Injecting custom icon..."))
                injectIcon(outputFile, iconUri)
            }
            emit(ApkProcessingResult.Progress(0.7f, "Icon processed"))

            emit(ApkProcessingResult.Progress(0.8f, "Signing APK..."))

            // Step 5: Sign the APK using apksig library
            val signedApk = signApkWithApkSig(outputFile)
            emit(ApkProcessingResult.Progress(0.9f, "APK signed"))

            // Cleanup template
            templateFile.delete()

            emit(ApkProcessingResult.Progress(1.0f, "Complete!"))
            
            // Emit ReadyToSave with temp file path and suggested filename
            val suggestedFileName = "${sanitizedName}.apk"
            emit(ApkProcessingResult.ReadyToSave(signedApk.absolutePath, suggestedFileName))

        } catch (e: Exception) {
            emit(ApkProcessingResult.Error("Failed to generate APK: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Result class for save operation
     */
    sealed class SaveResult {
        data object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
    
    /**
     * Copies the generated APK from cache to the user-selected destination
     */
    suspend fun saveApkToUri(tempFilePath: String, destinationUri: Uri): SaveResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(tempFilePath)
            if (!tempFile.exists()) {
                return@withContext SaveResult.Error("Temporary file not found")
            }
            
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext SaveResult.Error("Could not open destination file")
            
            // Clean up temp file after successful copy
            tempFile.delete()
            SaveResult.Success
        } catch (e: Exception) {
            SaveResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Copies the template APK from assets to the cache directory
     */
    private suspend fun copyTemplateToCache(): File = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "template_${System.currentTimeMillis()}.apk")

        context.assets.open(TEMPLATE_APK).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        cacheFile
    }

    /**
     * Modifies the APK template by:
     * 1. Injecting config.json with user's URL and settings
     * 2. Modifying AndroidManifest.xml to change the package name
     * 3. Modifying resources.arsc to change the app name
     * Uses Zip4j library for ZIP manipulation
     */
    private suspend fun modifyApk(
        templateFile: File,
        outputFile: File,
        url: String,
        appName: String,
        packageId: String,
        statusBarDark: Boolean,
        enableOfflineCache: Boolean
    ) = withContext(Dispatchers.IO) {
        // Copy template to output location
        templateFile.copyTo(outputFile, overwrite = true)

        // Create config.json content with all settings
        val configJson = JSONObject().apply {
            put("url", url)
            put("appName", appName)
            put("packageId", packageId)
            put("statusBarDark", statusBarDark)
            put("enableOfflineCache", enableOfflineCache)
            put("generatedAt", System.currentTimeMillis())
            put("version", "1.0")
        }

        // Write config to temp file
        val configFile = File(context.cacheDir, "config.json")
        configFile.writeText(configJson.toString(2))

        try {
            ZipFile(outputFile).use { zipFile ->
                // Remove existing config.json if present
                if (zipFile.getFileHeader(CONFIG_FILE) != null) {
                    zipFile.removeFile(CONFIG_FILE)
                }

                // Add new config.json
                val configParams = ZipParameters().apply {
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                    fileNameInZip = CONFIG_FILE
                }

                zipFile.addFile(configFile, configParams)
                
                // Modify AndroidManifest.xml to change package name
                val manifestHeader = zipFile.getFileHeader(MANIFEST_FILE)
                if (manifestHeader != null) {
                    // Extract manifest bytes
                    val manifestBytes = zipFile.getInputStream(manifestHeader).use { it.readBytes() }
                    
                    // Modify the package name in the binary manifest
                    // This will throw if the package name is too long or not found
                    val modifiedManifest = BinaryManifestModifier.modifyPackageName(
                        manifestBytes,
                        TEMPLATE_PACKAGE_ID,
                        packageId
                    )
                    
                    // Write modified manifest to temp file
                    val manifestTempFile = File(context.cacheDir, "AndroidManifest.xml")
                    manifestTempFile.writeBytes(modifiedManifest)
                    
                    try {
                        // Remove old manifest
                        zipFile.removeFile(MANIFEST_FILE)
                        
                        // Add modified manifest - use DEFLATE as original
                        val manifestParams = ZipParameters().apply {
                            compressionMethod = CompressionMethod.DEFLATE
                            compressionLevel = CompressionLevel.NORMAL
                            fileNameInZip = MANIFEST_FILE
                        }
                        
                        zipFile.addFile(manifestTempFile, manifestParams)
                    } finally {
                        manifestTempFile.delete()
                    }
                } else {
                    throw IllegalStateException("AndroidManifest.xml not found in template APK")
                }
                
                // Modify resources.arsc to change app name
                val resourcesHeader = zipFile.getFileHeader(RESOURCES_FILE)
                if (resourcesHeader != null) {
                    // Extract resources bytes
                    val resourcesBytes = zipFile.getInputStream(resourcesHeader).use { it.readBytes() }
                    
                    // Modify the app name in the binary resources
                    val modifiedResources = BinaryResourcesModifier.modifyString(
                        resourcesBytes,
                        TEMPLATE_APP_NAME,
                        appName
                    )
                    
                    // Write modified resources to temp file
                    val resourcesTempFile = File(context.cacheDir, "resources.arsc")
                    resourcesTempFile.writeBytes(modifiedResources)
                    
                    try {
                        // Remove old resources
                        zipFile.removeFile(RESOURCES_FILE)
                        
                        // Add modified resources - resources.arsc is stored uncompressed
                        val resourcesParams = ZipParameters().apply {
                            compressionMethod = CompressionMethod.STORE
                            fileNameInZip = RESOURCES_FILE
                        }
                        
                        zipFile.addFile(resourcesTempFile, resourcesParams)
                    } finally {
                        resourcesTempFile.delete()
                    }
                } else {
                    throw IllegalStateException("resources.arsc not found in template APK")
                }
            }
        } finally {
            configFile.delete()
        }
    }

    /**
     * Injects custom icon into the APK by replacing drawable/app_icon.png
     * This is a simple approach - just replace one drawable file
     */
    private suspend fun injectIcon(apkFile: File, iconUri: Uri) = withContext(Dispatchers.IO) {
        Log.d("ApkProcessor", "Starting icon injection from URI: $iconUri")
        
        // Load the source bitmap with proper resource handling
        val sourceBitmap = context.contentResolver.openInputStream(iconUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalStateException("Could not load icon image. Please try a different image file.")
        
        Log.d("ApkProcessor", "Source bitmap loaded: ${sourceBitmap.width}x${sourceBitmap.height}")

        try {
            // Scale to 192x192 (xxxhdpi size for best quality)
            val iconSize = 192
            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, iconSize, iconSize, true)
            
            // Convert to PNG bytes
            val bitmapOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, bitmapOutputStream)
            val pngBytes = bitmapOutputStream.toByteArray()
            
            Log.d("ApkProcessor", "Icon PNG size: ${pngBytes.size} bytes")
            
            ZipFile(apkFile).use { zipFile ->
                // List all resource entries for debugging
                val allPaths = zipFile.fileHeaders.map { it.fileName }
                val drawableEntries = allPaths.filter { it.startsWith("res/drawable") }
                val mipmapEntries = allPaths.filter { it.startsWith("res/mipmap") }
                Log.d("ApkProcessor", "Drawable entries: $drawableEntries")
                Log.d("ApkProcessor", "Mipmap entries: $mipmapEntries")
                
                // Find all icon files to replace (drawable AND mipmap)
                val iconPaths = allPaths.filter { path ->
                    (path.startsWith("res/drawable") || path.startsWith("res/mipmap")) &&
                    path.endsWith(".png") &&
                    !path.contains("_background") && // Skip background layers
                    (path.contains("app_icon") || path.contains("ic_launcher"))
                }
                
                Log.d("ApkProcessor", "Found icon paths to replace: $iconPaths")
                
                // Replace each icon file using stream API for correct path handling
                for (path in iconPaths) {
                    Log.d("ApkProcessor", "Replacing icon at: $path")
                    
                    // Remove existing file
                    zipFile.removeFile(path)
                    
                    // Add new icon using stream to ensure correct path
                    val zipParams = ZipParameters().apply {
                        compressionMethod = CompressionMethod.STORE
                        fileNameInZip = path
                    }
                    
                    java.io.ByteArrayInputStream(pngBytes).use { inputStream ->
                        zipFile.addStream(inputStream, zipParams)
                    }
                    
                    Log.d("ApkProcessor", "Added new icon at $path")
                }
                
                // If no icons found, add a default drawable icon
                if (iconPaths.isEmpty()) {
                    Log.d("ApkProcessor", "No icon files found, adding at res/drawable/app_icon.png")
                    
                    val zipParams = ZipParameters().apply {
                        compressionMethod = CompressionMethod.STORE
                        fileNameInZip = "res/drawable/app_icon.png"
                    }
                    
                    java.io.ByteArrayInputStream(pngBytes).use { inputStream ->
                        zipFile.addStream(inputStream, zipParams)
                    }
                }
                
                Log.d("ApkProcessor", "Icon injection completed successfully")
            }
            
            if (scaledBitmap != sourceBitmap) {
                scaledBitmap.recycle()
            }
        } finally {
            sourceBitmap.recycle()
        }
    }

    /**
     * Signs the APK using the apksig library with proper v1 and v2 signatures
     */
    private suspend fun signApkWithApkSig(apkFile: File): File = withContext(Dispatchers.IO) {
        val signedApk = File(
            apkFile.parentFile,
            apkFile.nameWithoutExtension + "_signed.apk"
        )

        try {
            // Load keystore from assets
            val keyStore = loadKeyStore()

            // Get private key and certificate
            val privateKey = keyStore.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray()) as PrivateKey
            val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate

            // Create signer config
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "CERT",
                privateKey,
                listOf(certificate)
            ).build()

            // Create ApkSigner
            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(apkFile)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build()

            // Sign the APK
            apkSigner.sign()

            // Delete unsigned APK
            apkFile.delete()

            signedApk
        } catch (e: Exception) {
            // If signing fails, throw exception with detailed error info
            throw RuntimeException("APK signing failed (${e.javaClass.simpleName}): ${e.message}", e)
        }
    }

    /**
     * Loads the keystore from assets
     */
    private fun loadKeyStore(): KeyStore {
        // Use PKCS12 format which is supported on Android (JKS is not available on Android)
        val keyStore = KeyStore.getInstance("PKCS12")

        context.assets.open(KEYSTORE_FILE).use { input ->
            keyStore.load(input, KEYSTORE_PASSWORD.toCharArray())
        }

        return keyStore
    }

    /**
     * Sanitizes file name for use as APK filename
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50)
    }
}
