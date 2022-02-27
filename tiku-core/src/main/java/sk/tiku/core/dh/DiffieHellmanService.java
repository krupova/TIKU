package sk.tiku.core.dh;

import sk.tiku.core.logging.Logger;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * DiffieHellman key agreement service
 *
 */
public class DiffieHellmanService {

    /**
     * Generate key pair for DH
     *
     * @param spec Option spec to define generated key pair
     * @return KeyPair
     */
    public static KeyPair generateKeyPair(DHParameterSpec spec) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
            if (spec == null) {
                keyPairGenerator.initialize(2048);
            } else {
                keyPairGenerator.initialize(spec);
            }
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            Logger.getInstance().error("Could not generate DH key pair", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialization of DH key agreement. This is done by party that initializes communication
     *
     * @return Initialized Key agreement
     */
    public static KeyAgreement initializeAgreement(PrivateKey privateKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("DH");
            keyAgreement.init(privateKey);
            return keyAgreement;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Logger.getInstance().error("Could not generate DH key agreement", e);
            throw new RuntimeException(e);
        }
    }

    public static PublicKey parsePublicKey(byte[] encodedPublicKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("DH");
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(encodedPublicKey);
            return keyFactory.generatePublic(x509KeySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Logger.getInstance().error("Could not parse public key", e);
            throw new RuntimeException(e);
        }
    }
}
