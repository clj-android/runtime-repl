package clojure.lang;

import android.util.Log;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.DexFile;
import dalvik.system.InMemoryDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;

/**
 * Android-aware classloader for dynamic Clojure compilation on ART.
 *
 * <p>When Clojure's compiler generates JVM bytecode at runtime (e.g. during
 * REPL evaluation), ART cannot load it directly because it requires DEX format.
 * This classloader intercepts {@code defineClass} calls, translates JVM bytecode
 * to DEX using the AOSP dx library, then loads the DEX using
 * {@link InMemoryDexClassLoader} (available since API 26).</p>
 *
 * <p>This class is loaded reflectively by the patched {@code RT.makeClassLoader()}
 * when running on the Dalvik/ART VM. It is only included in debug builds via
 * the {@code runtime-repl} dependency.</p>
 */
public class AndroidDynamicClassLoader extends DynamicClassLoader {

    private static final String TAG = "AndroidDynCL";
    private static final CfOptions CF_OPTIONS = new CfOptions();
    private static final DexOptions DEX_OPTIONS = new DexOptions();

    /**
     * Tracks the class name currently being defined, per thread.
     *
     * <p>When {@link #defineClass} is in progress, the InMemoryDexClassLoader
     * delegates to this classloader (its parent) via {@code loadClass}. Without
     * this guard, the parent would find the OLD class in {@code classCache} and
     * return it — the new DEX bytecodes would never be consulted. By tracking
     * which class is being redefined, {@link #loadClass} can throw
     * {@code ClassNotFoundException} for that specific class, forcing the
     * InMemoryDexClassLoader to fall through to its own {@code findClass} and
     * load from the fresh DEX bytes.</p>
     */
    private static final ThreadLocal<String> classBeingDefined = new ThreadLocal<>();

    static {
        CF_OPTIONS.strictNameCheck = false;
        DEX_OPTIONS.minSdkVersion = 26;
    }

    public AndroidDynamicClassLoader() {
        super();
    }

    public AndroidDynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Overrides DynamicClassLoader.defineClass to translate JVM bytecode to DEX
     * and load via InMemoryDexClassLoader.
     *
     * <p>The stock DynamicClassLoader calls {@code ClassLoader.defineClass(name,
     * bytes, 0, bytes.length)} which fails on ART because ART cannot interpret
     * JVM bytecode. This override converts the bytecode to DEX format first.</p>
     */
    @Override
    public Class defineClass(String name, byte[] bytes, Object srcForm) {
        try {
            // Mark this class as being redefined so loadClass won't return
            // stale versions from the cache or parent classloader chain.
            classBeingDefined.set(name);

            // Remove stale cache entry before loading the new version.
            classCache.remove(name);

            // Translate JVM bytecode to DEX using the dx library
            DxContext dxContext = new DxContext();
            DexFile dexFile = new DexFile(DEX_OPTIONS);
            DirectClassFile cf = new DirectClassFile(bytes, classNameToPath(name), false);
            cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
            dexFile.add(CfTranslator.translate(dxContext, cf, bytes, CF_OPTIONS, DEX_OPTIONS, dexFile));

            // Write DEX to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dexFile.writeTo(baos, null, false);
            byte[] dexBytes = baos.toByteArray();

            // Load via InMemoryDexClassLoader (API 26+, no temp files needed).
            // The dexLoader uses this classloader as its parent for delegation.
            // Our loadClass override ensures the class being defined is NOT found
            // via the parent, forcing dexLoader to load from the fresh DEX bytes.
            ByteBuffer dexBuffer = ByteBuffer.wrap(dexBytes);
            InMemoryDexClassLoader dexLoader = new InMemoryDexClassLoader(dexBuffer, this);

            Class<?> c = dexLoader.loadClass(name);
            if (c == null) {
                throw new ClassNotFoundException(
                    "dx/InMemoryDexClassLoader returned null for " + name);
            }

            // Register in the shared classCache so Clojure's RT.classForName()
            // and DynamicClassLoader.findInMemoryClass() can find it later.
            Util.clearCache(rq, classCache);
            classCache.put(name, new SoftReference(c, rq));

            return c;
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Failed to load DEX-translated class: " + name, e);
            throw new RuntimeException("Failed to define class " + name + " on Android", e);
        } catch (IOException e) {
            Log.e(TAG, "I/O error translating class to DEX: " + name, e);
            throw new RuntimeException("Failed to define class " + name + " on Android", e);
        } finally {
            classBeingDefined.remove();
        }
    }

    /**
     * Prevents returning stale class definitions during function redefinition.
     *
     * <p>When InMemoryDexClassLoader.loadClass delegates to this parent
     * classloader, we must NOT return the old version of a class that is
     * currently being redefined. Throwing ClassNotFoundException forces
     * InMemoryDexClassLoader to fall through to its own findClass(), which
     * loads from the fresh DEX bytes containing the new function body.</p>
     *
     * <p>This handles both cases:</p>
     * <ul>
     *   <li>Old class in classCache (from a previous REPL definition)</li>
     *   <li>Old class in parent classloader chain (from AOT compilation)</li>
     * </ul>
     */
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        if (name.equals(classBeingDefined.get())) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, resolve);
    }

    private static String classNameToPath(String name) {
        return name.replace('.', '/') + ".class";
    }
}
