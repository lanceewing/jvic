package emu.jvic.teavm;

import java.io.File;

import org.teavm.vm.TeaVMOptimizationLevel;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompiler;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend;

public class BuildTeaVMJVic {

    public static void main(String[] args) {
        new TeaCompiler(new WebBackend())
                .addAssets(new AssetFileHandle("../assets"))
                .setOutputName("jvic")
                .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
                .setMainClass(TeaVMLauncher.class.getName())
                .setObfuscated(false)
                .addReflectionClass("emu.jvic.config.**")
                .addReflectionClass("com.badlogic.gdx.scenes.scene2d.ui.**")
                .addReflectionClass("com.badlogic.gdx.scenes.scene2d.utils.**")
                .build(new File("build/dist"));
    }
}