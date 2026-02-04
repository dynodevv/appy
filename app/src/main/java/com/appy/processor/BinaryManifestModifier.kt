package com.appy.processor

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary AndroidManifest.xml modifier
 * 
 * Android's compiled AndroidManifest.xml uses a binary format where strings are stored
 * in a string pool. For UTF-16 strings (the default), the format is:
 * - 2 bytes: character count (little-endian, uint16)
 * - N*2 bytes: UTF-16LE encoded characters
 * - 2 bytes: null terminator (0x0000)
 * 
 * To replace a package name, we need to:
 * 1. Find the UTF-16LE encoded string
 * 2. Update the character count (2 bytes before the string)
 * 3. Write the new string data
 * 4. Ensure proper null termination
 */
object BinaryManifestModifier {
    
    /**
     * Modifies the package name in a binary AndroidManifest.xml
     * 
     * @param manifestBytes The original binary manifest bytes
     * @param oldPackageName The package name to find (template package ID)
     * @param newPackageName The new package name to use (user's custom package ID)
     * @return Modified manifest bytes
     * @throws IllegalArgumentException if new package name is longer than the template
     */
    fun modifyPackageName(
        manifestBytes: ByteArray,
        oldPackageName: String,
        newPackageName: String
    ): ByteArray {
        if (newPackageName.length > oldPackageName.length) {
            throw IllegalArgumentException(
                "Package ID too long (max ${oldPackageName.length} chars). Please use a shorter package ID."
            )
        }
        
        val result = manifestBytes.copyOf()
        var replaced = false
        
        // Android binary XML primarily uses UTF-16LE for strings in the string pool
        val oldBytesUtf16 = encodeUtf16Le(oldPackageName)
        val newBytesUtf16 = encodeUtf16Le(newPackageName)
        
        var i = 0
        while (i < result.size - oldBytesUtf16.size) {
            if (matchesAt(result, i, oldBytesUtf16)) {
                // Found the old package name in UTF-16LE format
                
                // Check if there's a valid length prefix 2 bytes before
                // The length prefix stores character count (not byte count) as uint16 LE
                if (i >= 2) {
                    val charCount = (result[i - 2].toInt() and 0xFF) or 
                                   ((result[i - 1].toInt() and 0xFF) shl 8)
                    
                    // Verify this looks like the correct length prefix
                    if (charCount == oldPackageName.length) {
                        // Update the character count to the new string length
                        result[i - 2] = (newPackageName.length and 0xFF).toByte()
                        result[i - 1] = ((newPackageName.length shr 8) and 0xFF).toByte()
                    }
                }
                
                // Write the new package name (UTF-16LE encoded)
                for (j in newBytesUtf16.indices) {
                    result[i + j] = newBytesUtf16[j]
                }
                
                // Write null terminator immediately after the new string data
                val nullTermPos = i + newBytesUtf16.size
                if (nullTermPos + 1 < result.size) {
                    result[nullTermPos] = 0
                    result[nullTermPos + 1] = 0
                }
                
                // Clear any remaining bytes from the old string (after null terminator)
                // The old string's null terminator was at oldBytesUtf16.size, so clear from
                // newBytesUtf16.size + 2 up to oldBytesUtf16.size + 2
                for (j in (newBytesUtf16.size + 2) until (oldBytesUtf16.size + 2)) {
                    if (i + j < result.size) {
                        result[i + j] = 0
                    }
                }
                
                replaced = true
                i += oldBytesUtf16.size + 2 // Skip past the replaced string and its null terminator
            } else {
                i++
            }
        }
        
        if (!replaced) {
            throw IllegalStateException("Could not find template package ID in manifest")
        }
        
        return result
    }
    
    /**
     * Check if bytes at position match the target bytes
     */
    private fun matchesAt(data: ByteArray, position: Int, target: ByteArray): Boolean {
        if (position + target.size > data.size) return false
        for (i in target.indices) {
            if (data[position + i] != target[i]) return false
        }
        return true
    }
    
    /**
     * Encode string as UTF-16LE (little endian, no BOM)
     */
    private fun encodeUtf16Le(str: String): ByteArray {
        val buffer = ByteBuffer.allocate(str.length * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (char in str) {
            buffer.putChar(char)
        }
        return buffer.array()
    }
}
