/**
 * Copyright 2008 Brenden Matthews <brenden@diddyinc.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ompldr.server

import org.ompldr.server.RestApi.settings
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES {
  private val IV = settings.property("ompldr.secrets.aes_iv").getString()

  private fun encryptCipher(encryptionKey: String): Cipher {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    val key = SecretKeySpec(encryptionKey.toByteArray(charset("UTF-8")), "AES")
    cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(IV.toByteArray(charset("UTF-8"))))
    return cipher
  }

  fun encrypt(plainText: String, encryptionKey: String): ByteArray {
    val cipher = encryptCipher(encryptionKey)
    return cipher.doFinal(plainText.toByteArray())
  }

  fun getEncryptStream(encryptionKey: String, outputStream: OutputStream): CipherOutputStream {
    val cipher = encryptCipher(encryptionKey)
    return CipherOutputStream(outputStream, cipher)
  }

  private fun decryptCipher(encryptionKey: String): Cipher {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    val key = SecretKeySpec(encryptionKey.toByteArray(charset("UTF-8")), "AES")
    cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(IV.toByteArray(charset("UTF-8"))))
    return cipher
  }

  fun decrypt(cipherText: ByteArray, encryptionKey: String): String {
    val cipher = decryptCipher(encryptionKey)
    return String(cipher.doFinal(cipherText), charset("UTF-8"))
  }

  fun getDecryptStream(encryptionKey: String, outputStream: OutputStream): CipherOutputStream {
    val cipher = decryptCipher(encryptionKey)
    return CipherOutputStream(outputStream, cipher)
  }
}
