/**
 * Copyright (C) 2023 Zenichi Amano.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.crow_misia.aws.appsync.signv4

import java.io.InputStream
import java.nio.ByteBuffer

internal class ByteBufferInputStream(private val buffer: ByteBuffer) : InputStream() {
    override fun markSupported() = true

    override fun mark(readlimit: Int) {
        buffer.mark()
    }

    override fun reset() {
        buffer.reset()
    }

    @Synchronized
    override fun read(): Int {
        return if (buffer.hasRemaining()) {
            buffer.get().toInt()
        } else {
            -1
        }
    }

    @Synchronized
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val remaining = buffer.remaining()
        if (remaining == 0) {
            return -1
        }
        val newLen = minOf(len, remaining)
        buffer.get(b, off, newLen)
        return newLen
    }

    override fun available(): Int {
        return buffer.remaining()
    }

    override fun skip(n: Long): Long {
        return if (n > 0) {
            val m = minOf(n, buffer.remaining().toLong())
            val pos = buffer.position()
            buffer.position(pos + m.toInt())
            m
        } else 0
    }
}
