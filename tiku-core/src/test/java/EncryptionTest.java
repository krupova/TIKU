import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sk.tiku.core.encryption.EncryptionService;
import sk.tiku.core.logging.LogRetention;
import sk.tiku.core.logging.Logger;

public class EncryptionTest {
    private static EncryptionService encryptionService;
    //CONSTANTS FOR TESTS
    private static final String TEXT_1 = "Foo bar.";
    private static final String TEXT_2 = "BAZ BAZ";
    private static final String EK_1 = "FOO";
    private static final String EK_2 = "BAR";

    @BeforeAll
    static void setup() {
        //NOTE: For test, we disable secure random, because it takes long time if we need multiple random values fast.
        Logger.initLogger(LogRetention.DEBUG);
        encryptionService = new EncryptionService(false);
    }

    @Test
    void testBasicEncryption() {
        //TEST THAT ENCRYPTION AND DECRYPTION WORKS
        String encryptedString = encryptionService.encrypt(TEXT_1, EK_1);
        //AFTER ENCRYPTION, TEXT SHOULD CHANGE
        Assertions.assertNotEquals(encryptedString, TEXT_1);
        String decryptedString = encryptionService.decrypt(encryptedString, EK_1);
        //AFTER DECRYPTION, TEXT SHOULD CHANGE
        Assertions.assertNotEquals(decryptedString, encryptedString);
        //WE SHOULD GET BACK TEXT_1 AFTER DECRYPTION
        Assertions.assertEquals(decryptedString, TEXT_1);
    }

    @Test
    void testMultiRoundEncryption() {
        //TEST THAT ENCRYPTION AND DECRYPTION WORKS EVEN WHEN ENCRYPTING MULTIPLE TIMES
        String encryptedStringR1 = encryptionService.encrypt(TEXT_1, EK_1);
        //AFTER ENCRYPTION, TEXT SHOULD CHANGE
        Assertions.assertNotEquals(encryptedStringR1, TEXT_1);
        //TEST THAT ENCRYPTION AND DECRYPTION WORKS EVEN WHEN ENCRYPTING MULTIPLE TIMES
        String encryptedStringR2 = encryptionService.encrypt(encryptedStringR1, EK_2);
        //AFTER ENCRYPTION, TEXT SHOULD CHANGE
        Assertions.assertNotEquals(encryptedStringR2, encryptedStringR1);
        String decryptedStringR1 = encryptionService.decrypt(encryptedStringR2, EK_2);
        //AFTER DECRYPTION, TEXT SHOULD CHANGE
        Assertions.assertNotEquals(decryptedStringR1, encryptedStringR2);
        //WE SHOULD GET BACK encryptedStringR1 AFTER DECRYPTION
        Assertions.assertEquals(decryptedStringR1, encryptedStringR1);
        String decryptedStringR2 = encryptionService.decrypt(decryptedStringR1, EK_1);
        //AFTER DECRYPTION, TEXT SHOULD CHANGE
        Assertions.assertNotEquals(decryptedStringR1, decryptedStringR2);
        //WE SHOULD GET BACK TEXT_1 AFTER DECRYPTION
        Assertions.assertEquals(decryptedStringR2, TEXT_1);

    }

    @Test
    void testEncryptionUniq() {
        //TEST THAT ENCRYPTION PRODUCES DIFFERENT VALUES FOR DIFFERENT KEYS
        String encryptedString = encryptionService.encrypt(TEXT_1, EK_1);
        String encryptedString2 = encryptionService.encrypt(TEXT_1, EK_2);
        Assertions.assertNotEquals(encryptedString, encryptedString2);
    }

    @Test
    void testEncryptionUniq2() {
        //TEST THAT ENCRYPTION PRODUCES DIFFERENT VALUES FOR DIFFERENT TEXTS
        String encryptedString = encryptionService.encrypt(TEXT_1, EK_1);
        String encryptedString2 = encryptionService.encrypt(TEXT_2, EK_1);
        Assertions.assertNotEquals(encryptedString, encryptedString2);
    }

    @Test
    void testWrongKeyDecryption() {
        //TEST THAT ENCRYPTION WILL PRODUCE GARBAGE IF WE USE WRONG KEY FOR DECRYPTION
        String encryptedString = encryptionService.encrypt(TEXT_1, EK_1);
        Assertions.assertThrows(RuntimeException.class, () -> encryptionService.decrypt(encryptedString, EK_2));

    }
}
