import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import kotlin.coroutines.*;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

public class AuditProbe {
    static final String DATA = "com.whatuneed.ovisiontv.kotlin.data.";

    static Object singleton(String name) throws Exception {
        return Class.forName(DATA + name).getField("INSTANCE").get(null);
    }

    static boolean kids(String name) throws Exception {
        String json = "[{\"id\":\"p2\",\"name\":\"" + name + "\",\"emoji\":\"🍿\",\"color\":3,\"kids\":true}]";
        Object parsed = Class.forName(DATA + "ProfilesKt")
                .getMethod("parseProfiles", String.class, String.class).invoke(null, json, "Me");
        List<?> profiles = (List<?>) parsed.getClass().getMethod("getProfiles").invoke(parsed);
        return (boolean) profiles.get(0).getClass().getMethod("getKids").invoke(profiles.get(0));
    }

    static boolean matches(String salt, String expected) throws Exception {
        CompletableFuture<Object> result = new CompletableFuture<>();
        Continuation<Object> continuation = new Continuation<>() {
            public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
            public void resumeWith(Object value) { result.complete(value); }
        };
        Object returned = Class.forName(DATA + "KidsCrypto")
                .getMethod("matches", String.class, String.class, String.class, Continuation.class)
                .invoke(singleton("KidsCrypto"), salt, expected, "1234", continuation);
        Object value = returned == IntrinsicsKt.getCOROUTINE_SUSPENDED()
                ? result.get(5, TimeUnit.SECONDS) : returned;
        kotlin.ResultKt.throwOnFailure(value);
        return (boolean) value;
    }

    static byte[] plist(int depth) {
        int count = depth + 1;
        int objectsSize = 1 + depth * 3;
        int table = 8 + objectsSize;
        byte[] bytes = new byte[table + count + 32];
        System.arraycopy("bplist00".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 8);
        bytes[8] = 9;
        for (int i = 1; i <= depth; i++) {
            int offset = 9 + (i - 1) * 3;
            bytes[offset] = (byte) 0xa2;
            bytes[offset + 1] = bytes[offset + 2] = (byte) (i - 1);
        }
        for (int i = 0; i < count; i++) bytes[table + i] = (byte) (i == 0 ? 8 : 9 + (i - 1) * 3);
        ByteBuffer trailer = ByteBuffer.wrap(bytes);
        trailer.position(bytes.length - 32 + 6);
        trailer.put((byte) 1).put((byte) 1).putLong(count).putLong(depth).putLong(table);
        return bytes;
    }

    static long arrays(Object value) {
        if (!(value instanceof List<?> list)) return 0;
        long count = 1;
        for (Object child : list) count += arrays(child);
        return count;
    }

    public static void main(String[] args) throws Exception {
        if (!kids("Child") || kids("  ")) throw new AssertionError("Profile counterexample changed");
        System.out.println("PROFILE: healthy kids=true; blank-name repair kids=false (reproduced)");

        String salt = Base64.getEncoder().encodeToString(new byte[16]);
        int rejectedByException = 0;
        for (String[] input : List.of(new String[]{"!", "pbkdf2$sha256$1$ignored"},
                new String[]{salt, "pbkdf2$sha256$0$ignored"})) {
            try { matches(input[0], input[1]); }
            catch (IllegalArgumentException expected) { rejectedByException++; }
        }
        if (rejectedByException != 2) throw new AssertionError("Credential counterexamples changed");
        Method kdf = Class.forName(DATA + "KidsCrypto").getDeclaredMethod("pbkdf2", String.class, String.class, int.class);
        kdf.setAccessible(true);
        String digest = Base64.getEncoder().encodeToString((byte[]) kdf.invoke(singleton("KidsCrypto"), salt, "1234", 1));
        boolean acceptsWrongAlgorithm = matches(salt, "pbkdf2$other$1$" + digest);
        if (!acceptsWrongAlgorithm) throw new AssertionError("Algorithm counterexample changed");
        System.out.println("CREDENTIAL: malformed salt and zero iterations throw; unsupported algorithm tag accepted (JVM helper reproduced, Android crash not run)");

        Class<?> appClass = Class.forName(DATA + "TvApp");
        Object app = appClass.getConstructor(String.class, String.class, String.class, String.class, String.class, boolean.class)
                .newInstance("example.app", "example.app.Main", "Example", null, null, false);
        List<?> apps = List.of(app);
        Class<?> orders = Class.forName(DATA + "HomeOrderStoreKt");
        String raw = "[{\"name\":\"Folder\",\"apps\":[\"example.app\"]}]";
        Method build = orders.getMethod("buildHomeOrder", String.class, List.class, String.class, String.class);
        Object first = ((List<?>) build.invoke(null, raw, apps, "Folder", "Hidden")).get(0);
        Object second = ((List<?>) build.invoke(null, raw, apps, "Folder", "Hidden")).get(0);
        String firstId = (String) first.getClass().getMethod("getId").invoke(first);
        String secondId = (String) second.getClass().getMethod("getId").invoke(second);
        boolean covers = (boolean) orders.getMethod("savedOrderCovers", String.class, List.class).invoke(null, raw, apps);
        if (!covers || firstId.equals(secondId)) throw new AssertionError("Folder counterexample changed");
        System.out.println("FOLDER: missing-ID order covers all apps but gets a new ID on each parse (reproduced)");

        Class<?> themeClass = Class.forName(DATA + "SaverTheme");
        Object landscapes = themeClass.getField("LANDSCAPES").get(null);
        Object cities = themeClass.getField("CITIES").get(null);
        Object settings = singleton("SaverSettings");
        Set<?> hidden = (Set<?>) settings.getClass().getMethod("toggleGroup", Set.class, Set.class, int.class)
                .invoke(settings, Set.of(), Set.of("a"), 2);
        Set<?> remaining = (Set<?>) settings.getClass().getMethod("toggleTheme", Set.class, themeClass)
                .invoke(settings, Set.of(landscapes, cities), cities);
        Constructor<?> clip = Class.forName(DATA + "AerialClip").getConstructor(
                String.class, String.class, String.class, themeClass, String.class, String.class, int.class);
        List<?> clips = List.of(clip.newInstance("a", "A", "A", landscapes, "video", "preview", 0),
                clip.newInstance("b", "B", "B", cities, "video", "preview", 1));
        List<?> playlist = (List<?>) Class.forName(DATA + "SaverPlaylist").getMethod(
                "build", List.class, Set.class, Set.class, kotlin.random.Random.class)
                .invoke(singleton("SaverPlaylist"), clips, remaining, hidden, null);
        if (remaining.isEmpty() || !playlist.isEmpty()) throw new AssertionError("Playlist counterexample changed");
        System.out.println("SAVER: hiding A then disabling B leaves enabled themes but zero playable clips (reproduced)");

        Method parse = Class.forName(DATA + "BinaryPlist").getMethod("parse", byte[].class);
        for (int depth : new int[]{6, 10, 14}) {
            byte[] bytes = plist(depth);
            Object root = parse.invoke(singleton("BinaryPlist"), (Object) bytes);
            long expanded = arrays(root);
            if (expanded != (1L << depth) - 1) throw new AssertionError("Plist expansion changed");
            System.out.println("PLIST: depth=" + depth + " bytes=" + bytes.length + " allocated-array-nodes=" + expanded);
        }
        System.out.println("BOUNDED PROBES COMPLETE: no network, no Android settings writes, no depth-32/OOM payload executed");
    }
}
