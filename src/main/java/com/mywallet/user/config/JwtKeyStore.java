package com.mywallet.user.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Charge ou crée la paire RSA 2048 utilisée pour signer les jetons d'accès.
 * Choix de développement : les PEM sont sur le disque local. En production la clé
 * privée viendrait d'un coffre de secrets, pas d'un répertoire du serveur.
 */
@Component
public class JwtKeyStore {

	private static final String PRIVATE_FILE = "jwt-private.pem";
	private static final String PUBLIC_FILE = "jwt-public.pem";

	private final RSAPrivateKey privateKey;
	private final RSAPublicKey publicKey;
	private final String keyId;
	private final String modulus;
	private final String exponent;

	public JwtKeyStore(AppProperties properties) {
		Path directory = Path.of(properties.jwt().keysDir());
		try {
			Files.createDirectories(directory);
			Path privatePem = directory.resolve(PRIVATE_FILE);
			Path publicPem = directory.resolve(PUBLIC_FILE);
			boolean privateExists = Files.exists(privatePem);
			boolean publicExists = Files.exists(publicPem);
			KeyPair pair;
			if (privateExists && publicExists) {
				pair = new KeyPair(readPublic(publicPem), readPrivate(privatePem));
			}
			else if (privateExists || publicExists) {
				throw new IllegalStateException("Paire de clés JWT incomplète dans " + directory);
			}
			else {
				pair = generate();
				writePem(privatePem, "PRIVATE KEY", pair.getPrivate().getEncoded());
				writePem(publicPem, "PUBLIC KEY", pair.getPublic().getEncoded());
			}
			this.privateKey = (RSAPrivateKey) pair.getPrivate();
			this.publicKey = (RSAPublicKey) pair.getPublic();
			RSAKey material = new RSAKey.Builder(this.publicKey).build();
			this.keyId = material.computeThumbprint().toString();
			this.modulus = material.getModulus().toString();
			this.exponent = material.getPublicExponent().toString();
		}
		catch (IOException | GeneralSecurityException | JOSEException exception) {
			throw new IllegalStateException("Impossible de charger la paire de clés JWT", exception);
		}
	}

	public RSAPrivateKey privateKey() {
		return privateKey;
	}

	public RSAPublicKey publicKey() {
		return publicKey;
	}

	public String keyId() {
		return keyId;
	}

	public String modulus() {
		return modulus;
	}

	public String exponent() {
		return exponent;
	}

	private static KeyPair generate() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private static RSAPrivateKey readPrivate(Path path) throws IOException, GeneralSecurityException {
		byte[] der = decodePem(path, "PRIVATE KEY");
		return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
	}

	private static RSAPublicKey readPublic(Path path) throws IOException, GeneralSecurityException {
		byte[] der = decodePem(path, "PUBLIC KEY");
		return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
	}

	private static byte[] decodePem(Path path, String label) throws IOException {
		String pem = Files.readString(path, StandardCharsets.US_ASCII);
		String base64 = pem
				.replace("-----BEGIN " + label + "-----", "")
				.replace("-----END " + label + "-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(base64);
	}

	private static void writePem(Path destination, String label, byte[] der) throws IOException {
		String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
		String pem = "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
		Path temporary = Files.createTempFile(destination.getParent(), "jwt-", ".pem.tmp");
		Files.writeString(temporary, pem, StandardCharsets.US_ASCII);
		try {
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

}
