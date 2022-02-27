package sk.tiku.core.encryption;

import sk.tiku.core.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * EncryptionService for tiku network
 */
public class EncryptionService {

    /**
     * AES-256 USES 16 BYTE LONG IV
     */
    private static final int IV_LENGTH = 16;

    /**
     * Random generator, for generating random IV
     */
    private final Random random;

    /**
     * Constructor
     *
     * @param secureRandom Boolean flag if cryptosecure random should be used. Only tests should set this to false
     */
    public EncryptionService(boolean secureRandom) {
        try {
            this.random = secureRandom ? SecureRandom.getInstanceStrong() : new Random();
        } catch (NoSuchAlgorithmException e) {
            Logger.getInstance().error("Could not initialize random generator", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Encrypt using string encryption key
     *
     * @param src           Data to be encrypted
     * @param encryptionKey String to use as key. SHA-256 hash value of this key will be used as encryption key to
     *                      ensure correct length of the key
     * @return Encrypted data
     */
    public String encrypt(String src, String encryptionKey) {
        return encrypt(src, prepareEncryptionKey(encryptionKey));
    }

    /**
     * Encrypt using key from byte array
     *
     * @param src           Data to be encrypted
     * @param encryptionKey Encrption key
     * @return EncryptedData
     */
    public String encrypt(String src, byte[] encryptionKey) {
        try {
            //RANDOM 16 BYTE LONG IV
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            //GET ENCRYPTION IMPLEMENTATION
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, 0, 16, "AES"), new IvParameterSpec(iv));
            byte[] encryptedBytes = cipher.doFinal(src.getBytes());

            //PREPEND IV TO ENCRYPTED DATA
            ByteBuffer buff = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            buff.put(iv);
            buff.put(encryptedBytes);

            //ENCODE AS BASE64 STRING
            return Base64.getEncoder().encodeToString(buff.array());
        } catch (Exception e) {
            Logger.getInstance().error("Encryption failed", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypt using string encryption key
     *
     * @param src           Data to be decrypted
     * @param decryptionKey String to use as key. SHA-256 hash value of this key will be used as encryption key to
     *                      ensure correct length of the key
     * @return Decrypted data
     */
    public String decrypt(String src, String decryptionKey) {
        return decrypt(src, prepareEncryptionKey(decryptionKey));
    }

    /**
     * Decrypt using key from byte array
     *
     * @param src           Data to be encrypted
     * @param decryptionKey Decryption key
     * @return Decrypted data
     */
    public String decrypt(String src, byte[] decryptionKey) {
        try {
            byte[] srcBytes = Base64.getDecoder().decode(src);
            //FIRST 16 BYTES IN ENCRYPTED TEXT IS IV
            byte[] iv = new byte[IV_LENGTH];
            byte[] encryptedBytes = new byte[srcBytes.length - IV_LENGTH];

            //SPLIT IV AND DATA
            System.arraycopy(srcBytes, 0, iv, 0, IV_LENGTH);
            System.arraycopy(srcBytes, IV_LENGTH, encryptedBytes, 0, srcBytes.length - IV_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptionKey, 0, 16, "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(encryptedBytes));
        } catch (Exception e) {
            Logger.getInstance().error("Decryption failed", e);
            throw new RuntimeException(e);
        }
    }


    private byte[] prepareEncryptionKey(String encryptionKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(encryptionKey.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            Logger.getInstance().error("Could not prepare EncryptionKey", e);
            throw new RuntimeException(e);
        }
    }
}
