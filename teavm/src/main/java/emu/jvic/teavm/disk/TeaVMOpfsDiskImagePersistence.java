package emu.jvic.teavm.disk;

import java.util.function.Consumer;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.jvic.config.AppConfigItem;
import emu.jvic.io.disk.persistence.DiskImagePersistence;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.DiskPersistenceKey;
import emu.jvic.io.disk.persistence.DiskPersistenceMetadata;
import emu.jvic.io.disk.persistence.DiskPersistenceSupport;

/**
 * TeaVM worker-side disk image persistence backed by OPFS.
 */
public class TeaVMOpfsDiskImagePersistence implements DiskImagePersistence {

    @JSFunctor
    interface ResolveCallback extends JSObject {
        void resolve(ArrayBuffer startupDiskImage, boolean persistent);
    }

    @Override
    public void resolve(AppConfigItem appConfigItem, byte[] originalDiskImage,
            Consumer<DiskImagePersistenceSession> onResolved) {
        DiskPersistenceKey key = DiskPersistenceSupport.createKey(appConfigItem, originalDiskImage);
        String programIdSource = DiskPersistenceSupport.getProgramIdSource(appConfigItem);
        JSObject state = createState();
        resolveStartupDisk(key.getProgramKey(), key.getOriginalDiskHash(),
                (startupDiskImage, persistent) -> {
                    byte[] resolvedDiskImage = (startupDiskImage != null)
                            ? toByteArray(startupDiskImage)
                            : originalDiskImage;
                    onResolved.accept(new TeaVMOpfsDiskImagePersistenceSession(state,
                            appConfigItem, key, programIdSource, originalDiskImage,
                            resolvedDiskImage, persistent));
                });
    }

    private byte[] toByteArray(ArrayBuffer arrayBuffer) {
        Uint8Array source = Uint8Array.create(arrayBuffer);
        byte[] data = new byte[arrayBuffer.getByteLength()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)(source.get(i) & 0xff);
        }
        return data;
    }

    private ArrayBuffer toArrayBuffer(byte[] data) {
        ArrayBuffer arrayBuffer = ArrayBuffer.create(data.length);
        Uint8Array target = Uint8Array.create(arrayBuffer);
        for (int i = 0; i < data.length; i++) {
            target.set(i, (short)(data[i] & 0xff));
        }
        return arrayBuffer;
    }

    @JSBody(script = "return { writeChain: Promise.resolve(), closed: false };")
    private static native JSObject createState();

    @JSBody(params = { "programKey", "originalDiskHash", "callback" }, script = ""
            + "var openExistingDir = function(parent, name) {"
            + "  return parent.getDirectoryHandle(name).catch(function() { return null; });"
            + "};"
            + "var openExistingFile = function(parent, name) {"
            + "  return parent.getFileHandle(name).catch(function() { return null; });"
            + "};"
            + "if (!(self.navigator && self.navigator.storage && self.navigator.storage.getDirectory)) {"
            + "  callback(null, false);"
            + "  return;"
            + "}"
            + "self.navigator.storage.getDirectory()"
            + "  .then(function(root) { return openExistingDir(root, 'JVic'); })"
            + "  .then(function(jvicDir) { return jvicDir ? openExistingDir(jvicDir, 'Disk Images') : null; })"
            + "  .then(function(diskImagesDir) { return diskImagesDir ? openExistingDir(diskImagesDir, 'v1') : null; })"
            + "  .then(function(versionDir) { return versionDir ? openExistingDir(versionDir, programKey) : null; })"
            + "  .then(function(programDir) { return programDir ? openExistingDir(programDir, originalDiskHash) : null; })"
            + "  .then(function(imageDir) { return imageDir ? openExistingFile(imageDir, 'disk.d64') : null; })"
            + "  .then(function(fileHandle) {"
            + "    if (!fileHandle) { callback(null, false); return null; }"
            + "    return fileHandle.getFile()"
            + "      .then(function(file) { return file.arrayBuffer(); })"
            + "      .then(function(buffer) { callback(buffer, true); });"
            + "  })"
            + "  .catch(function(error) { console.error('JVic TeaVM OPFS resolve failed', error); callback(null, false); });")
    private static native void resolveStartupDisk(String programKey, String originalDiskHash,
            ResolveCallback callback);

    @JSBody(params = { "state", "programKey", "originalDiskHash", "diskImageData", "metadataJson" }, script = ""
            + "var ensureDir = function(parent, name) {"
            + "  return parent.getDirectoryHandle(name, {create: true});"
            + "};"
            + "var writeFile = function(fileHandle, contents) {"
            + "  return fileHandle.createWritable().then(function(writable) {"
            + "    return writable.write(contents).then(function() { return writable.close(); }, function(error) { writable.abort(); throw error; });"
            + "  });"
            + "};"
            + "state.writeChain = (state.writeChain || Promise.resolve())"
            + "  .catch(function() {})"
            + "  .then(function() {"
            + "    if (state.closed) { return null; }"
            + "    return self.navigator.storage.getDirectory()"
            + "      .then(function(root) { return ensureDir(root, 'JVic'); })"
            + "      .then(function(jvicDir) { return ensureDir(jvicDir, 'Disk Images'); })"
            + "      .then(function(diskImagesDir) { return ensureDir(diskImagesDir, 'v1'); })"
            + "      .then(function(versionDir) { return ensureDir(versionDir, programKey); })"
            + "      .then(function(programDir) { return ensureDir(programDir, originalDiskHash); })"
            + "      .then(function(imageDir) { return Promise.all([imageDir.getFileHandle('disk.d64', {create: true}), imageDir.getFileHandle('meta.json', {create: true})]); })"
            + "      .then(function(handles) { return Promise.all([writeFile(handles[0], diskImageData), writeFile(handles[1], metadataJson)]); });"
            + "  })"
            + "  .catch(function(error) { console.error('JVic TeaVM OPFS write failed', error); });")
    private static native void queueWrite(JSObject state, String programKey,
            String originalDiskHash, ArrayBuffer diskImageData, String metadataJson);

    @JSBody(params = "state", script = "if (state) { state.closed = true; }")
    private static native void closeState(JSObject state);

    private class TeaVMOpfsDiskImagePersistenceSession implements DiskImagePersistenceSession {

        private final JSObject state;
        private final AppConfigItem appConfigItem;
        private final DiskPersistenceKey key;
        private final String programIdSource;
        private final int originalDiskSize;
        private final byte[] startupDiskImage;
        private boolean persistent;
        private long createdAtEpochMs;
        private long persistenceActivatedAtEpochMs;

        TeaVMOpfsDiskImagePersistenceSession(JSObject state, AppConfigItem appConfigItem,
                DiskPersistenceKey key, String programIdSource, byte[] originalDiskImage,
                byte[] startupDiskImage, boolean persistent) {
            this.state = state;
            this.appConfigItem = appConfigItem;
            this.key = key;
            this.programIdSource = programIdSource;
            this.originalDiskSize = (originalDiskImage != null) ? originalDiskImage.length : 0;
            this.startupDiskImage = startupDiskImage;
            this.persistent = persistent;
            if (persistent) {
                long now = System.currentTimeMillis();
                this.createdAtEpochMs = now;
                this.persistenceActivatedAtEpochMs = now;
            }
        }

        @Override
        public byte[] getStartupDiskImage() {
            return startupDiskImage;
        }

        @Override
        public boolean isPersistent() {
            return persistent;
        }

        @Override
        public void onDiskChanged(byte[] diskImageBytes) {
            long now = System.currentTimeMillis();
            if (!persistent) {
                persistent = true;
                createdAtEpochMs = now;
                persistenceActivatedAtEpochMs = now;
            }

            DiskPersistenceMetadata metadata = new DiskPersistenceMetadata(appConfigItem,
                    key, programIdSource, originalDiskSize);
            metadata.setCreatedAtEpochMs(createdAtEpochMs);
            metadata.setUpdatedAtEpochMs(now);
            metadata.setPersistenceActivatedAtEpochMs(persistenceActivatedAtEpochMs);
            metadata.setPersistedDiskHash(DiskPersistenceSupport.stableHashHex(diskImageBytes));

            queueWrite(state, key.getProgramKey(), key.getOriginalDiskHash(),
                    toArrayBuffer(diskImageBytes), metadata.toJson());
        }

        @Override
        public void close() {
            closeState(state);
        }
    }
}