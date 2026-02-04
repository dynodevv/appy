package com.appy.processor

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary resources.arsc modifier
 * 
 * Android's compiled resources.arsc contains a string pool with app strings like the app name.
 * The string pool can use either UTF-8 or UTF-16 encoding:
 * 
 * UTF-8 format (FLAG_UTF8 = 0x100 set):
 * - 1-2 bytes: character count (length encoded)
 * - 1-2 bytes: byte count (length encoded)
 * - N bytes: UTF-8 encoded characters
 * - 1 byte: null terminator
 * 
 * UTF-16 format (default):
 * - 2 bytes: character count (little-endian)
 * - N*2 bytes: UTF-16LE encoded characters
 * - 2 bytes: null terminator
 * 
 * This utility finds and replaces string values in the compiled resources.
 */
object BinaryResourcesModifier {
    
    /**
     * Modifies a string value in compiled resources.arsc
     * 
     * @param resourcesBytes The original resources.arsc bytes
     * @param oldValue The string to find (template placeholder)
     * @param newValue The new string to use (user's custom value)
     * @return Modified resources bytes
     * @throws IllegalArgumentException if new value is longer than the template
     */
    fun modifyString(
        resourcesBytes: ByteArray,
        oldValue: String,
        newValue: String
    ): ByteArray {
        if (newValue.length > oldValue.length) {
            throw IllegalArgumentException(
                "Value too long (max ${oldValue.length} chars). Please use a shorter value."
            )
        }
        
        val result = resourcesBytes.copyOf()
        
        // Try UTF-8 first (more common in modern Android builds)
        var replaced = tryReplaceUtf8(result, oldValue, newValue)
        
        // If UTF-8 not found, try UTF-16
        if (!replaced) {
            replaced = tryReplaceUtf16(result, oldValue, newValue)
        }
        
        if (!replaced) {
            throw IllegalStateException("Could not find template string in resources")
        }
        
        return result
    }
    
    /**
     * Try to find and replace UTF-8 encoded string
     */
    private fun tryReplaceUtf8(result: ByteArray, oldValue: String, newValue: String): Boolean {
        val oldBytesUtf8 = oldValue.toByteArray(Charsets.UTF_8)
        val newBytesUtf8 = newValue.toByteArray(Charsets.UTF_8)
        
        if (newBytesUtf8.size > oldBytesUtf8.size) {
            throw IllegalArgumentException(
                "Value too long in bytes. Please use a shorter value."
            )
        }
        
        var i = 0
        while (i < result.size - oldBytesUtf8.size) {
            if (matchesAt(result, i, oldBytesUtf8)) {
                // Found the old string in UTF-8 format
                // Verify null terminator follows
                val afterStr = i + oldBytesUtf8.size
                if (afterStr < result.size && result[afterStr] == 0.toByte()) {
                    // This looks like a valid UTF-8 string in resources.arsc
                    
                    // In UTF-8 string pool format, the length prefix is 1-2 bytes before the string
                    // We need to update the byte count (not char count) for UTF-8
                    // The format uses a length-encoded value where high bit indicates more bytes
                    
                    // Write the new string
                    for (j in newBytesUtf8.indices) {
                        result[i + j] = newBytesUtf8[j]
                    }
                    
                    // Write null terminator immediately after
                    result[i + newBytesUtf8.size] = 0
                    
                    // Clear remaining bytes from old string
                    for (j in (newBytesUtf8.size + 1) until (oldBytesUtf8.size + 1)) {
                        if (i + j < result.size) {
                            result[i + j] = 0
                        }
                    }
                    
                    // Try to update length prefix if we can find it
                    // UTF-8 string pool entries have: [charLen][byteLen][string][null]
                    // The lengths use varint encoding (high bit = more bytes)
                    if (i >= 2) {
                        // Check for simple 1-byte length prefix pattern
                        val prevByte = result[i - 1].toInt() and 0xFF
                        if (prevByte == oldBytesUtf8.size && prevByte < 0x80) {
                            result[i - 1] = newBytesUtf8.size.toByte()
                        }
                    }
                    
                    return true
                }
            }
            i++
        }
        
        return false
    }
    
    /**
     * Try to find and replace UTF-16LE encoded string
     */
    private fun tryReplaceUtf16(result: ByteArray, oldValue: String, newValue: String): Boolean {
        val oldBytesUtf16 = encodeUtf16Le(oldValue)
        val newBytesUtf16 = encodeUtf16Le(newValue)
        
        var i = 0
        while (i < result.size - oldBytesUtf16.size) {
            if (matchesAt(result, i, oldBytesUtf16)) {
                // Found the old string in UTF-16LE format
                
                // Check if there's a valid length prefix 2 bytes before
                if (i >= 2) {
                    val charCount = (result[i - 2].toInt() and 0xFF) or 
                                   ((result[i - 1].toInt() and 0xFF) shl 8)
                    
                    // Verify this looks like the correct length prefix
                    if (charCount == oldValue.length) {
                        // Update the character count to the new string length
                        result[i - 2] = (newValue.length and 0xFF).toByte()
                        result[i - 1] = ((newValue.length shr 8) and 0xFF).toByte()
                    }
                }
                
                // Write the new string (UTF-16LE encoded)
                for (j in newBytesUtf16.indices) {
                    result[i + j] = newBytesUtf16[j]
                }
                
                // Write null terminator immediately after the new string data
                val nullTermPos = i + newBytesUtf16.size
                if (nullTermPos + 1 < result.size) {
                    result[nullTermPos] = 0
                    result[nullTermPos + 1] = 0
                }
                
                // Clear any remaining bytes from the old string
                for (j in (newBytesUtf16.size + 2) until (oldBytesUtf16.size)) {
                    if (i + j < result.size) {
                        result[i + j] = 0
                    }
                }
                
                return true
            }
            i++
        }
        
        return false
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
