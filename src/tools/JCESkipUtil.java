package tools;

import java.lang.reflect.*;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Map;

import org.slf4j.Logger;

public class JCESkipUtil {
	/**
	 * https://stackoverflow.com/a/22492582
	 */
	public static void removeCryptographyRestrictions(Logger logger) {
	    if (!isRestrictedCryptography()) {
	        logger.info("Cryptography restrictions removal not needed");
	        return;
	    }
	    try {
	        /*
	         * Do the following, but with reflection to bypass access checks:
	         *
	         * JceSecurity.isRestricted = false;
	         * JceSecurity.defaultPolicy.perms.clear();
	         * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
	         */
	        final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
	        final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
	        final Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");
	
	        final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
	        isRestrictedField.setAccessible(true);
	        final Field modifiersField = Field.class.getDeclaredField("modifiers");
	        modifiersField.setAccessible(true);
	        modifiersField.setInt(isRestrictedField, isRestrictedField.getModifiers() & ~Modifier.FINAL);
	        isRestrictedField.set(null, false);
	
	        final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
	        defaultPolicyField.setAccessible(true);
	        final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);
	
	        final Field perms = cryptoPermissions.getDeclaredField("perms");
	        perms.setAccessible(true);
	        ((Map<?, ?>) perms.get(defaultPolicy)).clear();
	
	        final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
	        instance.setAccessible(true);
	        defaultPolicy.add((Permission) instance.get(null));
	
	        logger.info("Successfully removed cryptography restrictions");
	    } catch (final Exception e) {
	        logger.warn("Failed to remove cryptography restrictions", e);
	    }
	}
	
	private static boolean isRestrictedCryptography() {
	    // This matches Oracle Java 7 and 8, but not Java 9 or OpenJDK.
	    final String name = System.getProperty("java.runtime.name");
	    final String ver = System.getProperty("java.version");
	    return name != null && name.equals("Java(TM) SE Runtime Environment")
	            && ver != null && (ver.startsWith("1.7") || ver.startsWith("1.8"));
	}
}

