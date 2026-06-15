/*
 * 白い熊 fork: gocryptfs provider — seekable read/write channel over a decrypted file.
 *
 * gocryptfs supports random-access I/O at arbitrary offsets. Each native read/write transfers at
 * most MAX_KERNEL_WRITE (128 KiB) per call (DroidFS does the same), so reads/writes are chunked.
 * Size and truncation use the in-volume path (native_truncate operates on the path, with the file
 * open).
 */

package me.zhanghai.android.files.provider.gocryptfs

import me.zhanghai.android.files.provider.common.AbstractFileByteChannel
import me.zhanghai.android.files.provider.gocryptfs.client.GocryptfsVolume
import java.io.IOException
import java.nio.ByteBuffer

internal class GocryptfsFileByteChannel(
    private val volume: GocryptfsVolume,
    private val handle: Int,
    private val path: String,
    isAppend: Boolean
) : AbstractFileByteChannel(isAppend) {
    override fun onRead(position: Long, size: Int): ByteBuffer {
        val length = size.coerceAtMost(GocryptfsVolume.MAX_KERNEL_WRITE)
        val buffer = ByteArray(length)
        val read = volume.readFile(handle, position, buffer, 0L, length)
        if (read <= 0) {
            return ByteBuffer.allocate(0)
        }
        return ByteBuffer.wrap(buffer, 0, read)
    }

    override fun onWrite(position: Long, source: ByteBuffer) {
        val total = source.remaining()
        val array: ByteArray
        val arrayBase: Int
        if (source.hasArray()) {
            array = source.array()
            arrayBase = source.arrayOffset() + source.position()
        } else {
            array = ByteArray(total)
            source.duplicate().get(array)
            arrayBase = 0
        }
        var written = 0
        while (written < total) {
            val length = (total - written).coerceAtMost(GocryptfsVolume.MAX_KERNEL_WRITE)
            val n = volume.writeFile(
                handle, position + written, array, (arrayBase + written).toLong(), length
            )
            if (n <= 0) {
                throw IOException("native_write_file returned $n for $path")
            }
            written += n
        }
        source.position(source.position() + written)
    }

    override fun onTruncate(size: Long) {
        if (!volume.truncate(path, size)) {
            throw IOException("native_truncate failed for $path")
        }
    }

    override fun onSize(): Long = volume.getAttr(path)?.size ?: 0L

    override fun onClose() {
        volume.closeFile(handle)
    }
}
