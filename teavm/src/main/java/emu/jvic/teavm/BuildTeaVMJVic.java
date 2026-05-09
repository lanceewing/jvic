package emu.jvic.teavm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.teavm.vm.TeaVMOptimizationLevel;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompiler;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend;

public class BuildTeaVMJVic {

    private static final String WORKER_BUNDLE_NAME = "jvic-worker-bundle.js";
    private static final String WORKER_BOOTSTRAP_NAME = "jvic-worker.js";

    public static void main(String[] args) {
    File mainDist = new File("build/dist");
    File workerDist = new File("build/worker-dist");

        new TeaCompiler(new WebBackend())
                .addAssets(new AssetFileHandle("../assets"))
                .setOutputName("jvic")
                .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
                .setMainClass(TeaVMLauncher.class.getName())
                .setObfuscated(false)
                .addReflectionClass("emu.jvic.config.**")
                .addReflectionClass("com.badlogic.gdx.scenes.scene2d.ui.**")
                .addReflectionClass("com.badlogic.gdx.scenes.scene2d.utils.**")
        .build(mainDist);

    new TeaCompiler(new WebBackend())
        .setOutputName("jvic-worker")
        .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
        .setMainClass(TeaVMJVicWebWorker.class.getName())
        .setObfuscated(false)
        .build(workerDist);

    deleteFileIfExists(Path.of(mainDist.getPath(), "webapp", "autorun-config.txt"));
    copyFile(Path.of("webapp", "index.html"),
        Path.of(mainDist.getPath(), "webapp", "index.html"));
    copyFile(Path.of("../html/webapp/styles.css"),
        Path.of(mainDist.getPath(), "webapp", "styles.css"));
    copyFile(Path.of("../html/webapp/sound-renderer.js"),
        Path.of(mainDist.getPath(), "webapp", "sound-renderer.js"));
    copyFile(Path.of("../html/webapp/_headers"),
        Path.of(mainDist.getPath(), "webapp", "_headers"));
    copyFile(Path.of("../html/webapp/.swshtaccess"),
        Path.of(mainDist.getPath(), "webapp", ".swshtaccess"));
    copyFile(Path.of("../html/webapp/programs"),
        Path.of(mainDist.getPath(), "webapp", "programs"));
    copyFile(Path.of("../html/webapp/worker/.swshtaccess"),
        Path.of(mainDist.getPath(), "webapp", "scripts", ".swshtaccess"));
    copyFile(Path.of(workerDist.getPath(), "webapp", "jvic-worker.js"),
        Path.of(mainDist.getPath(), "webapp", "scripts", WORKER_BUNDLE_NAME));
    writeFile(Path.of(mainDist.getPath(), "webapp", "scripts", WORKER_BOOTSTRAP_NAME),
        createWorkerBootstrapScript());
    }

    private static void copyFile(Path source, Path target) {
    try {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
        throw new RuntimeException("Failed to copy " + source + " to " + target, e);
    }
    }

    private static void deleteFileIfExists(Path target) {
    try {
        Files.deleteIfExists(target);
    } catch (IOException e) {
        throw new RuntimeException("Failed to delete " + target, e);
    }
    }

    private static void writeFile(Path target, String content) {
    try {
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    } catch (IOException e) {
        throw new RuntimeException("Failed to write " + target, e);
    }
    }

    private static String createWorkerBootstrapScript() {
    return "function reportWorkerError(prefix, error, filename, lineno, colno) {\n"
            + "  var message = prefix || 'Worker error';\n"
            + "  if (filename) {\n"
            + "    message += '\\n' + filename + ':' + (lineno || 0) + ':' + (colno || 0);\n"
            + "  }\n"
            + "  if (error && error.stack) {\n"
            + "    message += '\\n' + error.stack;\n"
            + "  } else if (error) {\n"
            + "    message += '\\n' + String(error);\n"
            + "  }\n"
            + "  try {\n"
            + "    self.postMessage({ name: 'WorkerError', object: { message: message } });\n"
            + "  } catch (postMessageError) {\n"
            + "  }\n"
            + "}\n"
            + "self.addEventListener('error', function(event) {\n"
            + "  reportWorkerError((event && event.message) ? event.message : 'Worker error', event ? event.error : null, event ? event.filename : null, event ? event.lineno : 0, event ? event.colno : 0);\n"
            + "});\n"
            + "self.addEventListener('unhandledrejection', function(event) {\n"
            + "  reportWorkerError('Worker unhandled rejection', event ? event.reason : null, null, 0, 0);\n"
            + "});\n"
            + "try {\n"
            + "  importScripts('./" + WORKER_BUNDLE_NAME + "');\n"
            + "  self.main();\n"
            + "} catch (error) {\n"
            + "  reportWorkerError('Worker bootstrap failure', error, null, 0, 0);\n"
            + "  throw error;\n"
            + "}\n";
    }
}