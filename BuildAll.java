import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.stream.*;

public class BuildAll {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Starting AuthSecured Build Process ===");
        
        File depsDir = new File("build/deps");
        List<String> cpList = new ArrayList<>();
        if (depsDir.exists()) {
            for (File f : Objects.requireNonNull(depsDir.listFiles())) {
                if (f.getName().endsWith(".jar")) {
                    cpList.add(f.getAbsolutePath());
                }
            }
        }
        String classpath = String.join(File.pathSeparator, cpList);

        // 1. Compile Core
        System.out.println("\n[1/4] Compiling Core Module...");
        Path coreOut = Paths.get("build/classes/core");
        Files.createDirectories(coreOut);
        List<File> coreSources = findJavaFiles(new File("core/src/main/java"));
        
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        
        List<String> coreOptions = Arrays.asList("-classpath", classpath, "-d", coreOut.toString(), "--release", "21");
        Iterable<? extends JavaFileObject> coreUnits = fileManager.getJavaFileObjectsFromFiles(coreSources);
        
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, coreOptions, null, coreUnits);
        boolean coreSuccess = task.call();
        
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            System.out.println(d);
        }
        if (!coreSuccess) {
            throw new RuntimeException("Core compilation failed!");
        }
        System.out.println("Core compiled successfully to " + coreOut);

        // 2. Package Core Fat/Shaded Jar
        System.out.println("\n[2/4] Packaging Core Shaded Jar...");
        Path coreLibsDir = Paths.get("core/build/libs");
        Files.createDirectories(coreLibsDir);
        Path coreShadedJar = coreLibsDir.resolve("authsecured-core-1.0.3-shaded.jar");
        
        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(coreShadedJar)))) {
            // Add core compiled classes
            addDirectoryToJar(jos, coreOut.toFile(), coreOut.toString());
            
            // Add dependencies into shaded jar
            for (String dep : cpList) {
                File depFile = new File(dep);
                if (depFile.getName().contains("fabric") || depFile.getName().contains("mixin")) continue;
                try (JarFile jf = new JarFile(depFile)) {
                    Enumeration<JarEntry> entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith("META-INF/MANIFEST.MF") || name.startsWith("META-INF/INDEX.LIST") || name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")) continue;
                        if (entry.isDirectory()) continue;
                        try {
                            jos.putNextEntry(new JarEntry(name));
                            try (InputStream is = jf.getInputStream(entry)) {
                                is.transferTo(jos);
                            }
                            jos.closeEntry();
                        } catch (ZipException ignored) {}
                    }
                }
            }
        }
        System.out.println("Created Core Shaded Jar: " + coreShadedJar + " (" + Files.size(coreShadedJar) + " bytes)");

        // 3. Compile Fabric Module
        System.out.println("\n[3/4] Compiling Fabric Module...");
        Path fabricOut = Paths.get("build/classes/fabric");
        Files.createDirectories(fabricOut);
        List<File> fabricSources = findJavaFiles(new File("fabric/src/main/java"));
        
        String fabricClasspath = coreShadedJar.toAbsolutePath() + File.pathSeparator + classpath;
        List<String> fabricOptions = Arrays.asList("-classpath", fabricClasspath, "-d", fabricOut.toString(), "--release", "21");
        
        DiagnosticCollector<JavaFileObject> fabricDiag = new DiagnosticCollector<>();
        StandardJavaFileManager fabricFileManager = compiler.getStandardFileManager(fabricDiag, null, null);
        Iterable<? extends JavaFileObject> fabricUnits = fabricFileManager.getJavaFileObjectsFromFiles(fabricSources);
        
        JavaCompiler.CompilationTask fabricTask = compiler.getTask(null, fabricFileManager, fabricDiag, fabricOptions, null, fabricUnits);
        boolean fabricSuccess = fabricTask.call();
        
        for (Diagnostic<? extends JavaFileObject> d : fabricDiag.getDiagnostics()) {
            System.out.println(d);
        }
        if (!fabricSuccess) {
            throw new RuntimeException("Fabric compilation failed!");
        }
        System.out.println("Fabric compiled successfully to " + fabricOut);

        // 4. Package Final Mod Jar
        System.out.println("\n[4/4] Packaging Final Fabric Mod Jar...");
        Path fabricLibsDir = Paths.get("fabric/build/libs");
        Path rootLibsDir = Paths.get("build/libs");
        Files.createDirectories(fabricLibsDir);
        Files.createDirectories(rootLibsDir);
        
        Path finalJar = fabricLibsDir.resolve("authsecured-1.0.3.jar");
        
        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(finalJar)))) {
            // Add fabric compiled classes
            addDirectoryToJar(jos, fabricOut.toFile(), fabricOut.toString());
            
            // Add resources with expanded properties
            Path resDir = Paths.get("fabric/src/main/resources");
            if (Files.exists(resDir)) {
                try (Stream<Path> stream = Files.walk(resDir)) {
                    for (Path p : stream.collect(Collectors.toList())) {
                        if (Files.isDirectory(p)) continue;
                        String relPath = resDir.relativize(p).toString();
                        jos.putNextEntry(new JarEntry(relPath));
                        if (relPath.equals("fabric.mod.json")) {
                            String content = Files.readString(p).replace("${version}", "1.0.3");
                            jos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        } else {
                            Files.copy(p, jos);
                        }
                        jos.closeEntry();
                    }
                }
            }
            
            // Embed core shaded classes into final jar
            try (JarFile jf = new JarFile(coreShadedJar.toFile())) {
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("META-INF/MANIFEST.MF")) continue;
                    if (entry.isDirectory()) continue;
                    try {
                        jos.putNextEntry(new JarEntry(name));
                        try (InputStream is = jf.getInputStream(entry)) {
                            is.transferTo(jos);
                        }
                        jos.closeEntry();
                    } catch (ZipException ignored) {}
                }
            }
        }

        // Copy to build/libs/
        Path rootJar1 = rootLibsDir.resolve("authsecured-1.0.3.jar");
        Path rootJar2 = rootLibsDir.resolve("authsecured-fabric-1.0.3.jar");
        Files.copy(finalJar, rootJar1, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(finalJar, rootJar2, StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("=== BUILD SUCCESSFUL ===");
        System.out.println("Mod Jar: " + finalJar.toAbsolutePath() + " (" + Files.size(finalJar) + " bytes)");
        System.out.println("Root Jar: " + rootJar2.toAbsolutePath());
    }

    private static List<File> findJavaFiles(File dir) {
        List<File> list = new ArrayList<>();
        if (!dir.exists()) return list;
        File[] files = dir.listFiles();
        if (files == null) return list;
        for (File f : files) {
            if (f.isDirectory()) {
                list.addAll(findJavaFiles(f));
            } else if (f.getName().endsWith(".java")) {
                list.add(f);
            }
        }
        return list;
    }

    private static void addDirectoryToJar(JarOutputStream jos, File dir, String baseDir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                addDirectoryToJar(jos, f, baseDir);
            } else {
                String path = f.getAbsolutePath().substring(baseDir.length() + 1).replace('\\', '/');
                try {
                    jos.putNextEntry(new JarEntry(path));
                    try (InputStream is = new BufferedInputStream(new FileInputStream(f))) {
                        is.transferTo(jos);
                    }
                    jos.closeEntry();
                } catch (ZipException ignored) {}
            }
        }
    }
}
