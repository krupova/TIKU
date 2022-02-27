import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sk.tiku.core.dh.DiffieHellmanService;

import javax.crypto.KeyAgreement;
import javax.crypto.ShortBufferException;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;

public class DiffieHellmanTest {
    @Test
    void testBasicDhNegotiation() {
        try {
            KeyPair aliceKeyPair = DiffieHellmanService.generateKeyPair(null);
            KeyAgreement aliceKeyAgree = DiffieHellmanService.initializeAgreement(aliceKeyPair.getPrivate());

            // Alice encodes her public key, and sends it over to Bob.
            byte[] alicePubKeyEnc = aliceKeyPair.getPublic().getEncoded();

            /*
             * Let's turn over to Bob. Bob has received Alice's public key
             * in encoded format.
             * He instantiates a DH public key from the encoded key material.
             */
            PublicKey alicePubKey = DiffieHellmanService.parsePublicKey(alicePubKeyEnc);

            /*
             * Bob gets the DH parameters associated with Alice's public key.
             * He must use the same parameters when he generates his own key
             * pair.
             */
            DHParameterSpec dhParamFromAlicePubKey = ((DHPublicKey) alicePubKey).getParams();

            // Bob creates his own DH key pair
            KeyPair bobKpair = DiffieHellmanService.generateKeyPair(dhParamFromAlicePubKey);

            // Bob creates and initializes his DH KeyAgreement object
            KeyAgreement bobKeyAgree = DiffieHellmanService.initializeAgreement(bobKpair.getPrivate());

            // Bob encodes his public key, and sends it over to Alice.
            byte[] bobPubKeyEnc = bobKpair.getPublic().getEncoded();

            /*
             * Alice uses Bob's public key for the first (and only) phase
             * of her version of the DH
             * protocol.
             * Before she can do so, she has to instantiate a DH public key
             * from Bob's encoded key material.
             */
            PublicKey bobPubKey = DiffieHellmanService.parsePublicKey(bobPubKeyEnc);

            aliceKeyAgree.doPhase(bobPubKey, true);


            /*
             * Bob uses Alice's public key for the first (and only) phase
             * of his version of the DH
             * protocol.
             */
            bobKeyAgree.doPhase(alicePubKey, true);

            /*
             * At this stage, both Alice and Bob have completed the DH key
             * agreement protocol.
             * Both generate the (same) shared secret.
             */
            byte[] aliceSharedSecret = aliceKeyAgree.generateSecret();
            int aliceLen = aliceSharedSecret.length;

            byte[] bobSharedSecret = new byte[aliceLen];
            int bobLen;
            // provide output buffer of required size
            bobLen = bobKeyAgree.generateSecret(bobSharedSecret, 0);

            Assertions.assertEquals(aliceLen, bobLen);
            Assertions.assertArrayEquals(aliceSharedSecret, bobSharedSecret);
        } catch (InvalidKeyException | ShortBufferException e) {
            throw new RuntimeException();
        }
    }

}
