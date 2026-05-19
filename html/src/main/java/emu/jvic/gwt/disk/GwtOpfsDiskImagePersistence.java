package emu.jvic.gwt.disk;

import java.util.function.Consumer;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.TypedArrays;
import com.google.gwt.typedarrays.shared.Uint8Array;

import emu.jvic.config.AppConfigItem;
import emu.jvic.io.disk.persistence.DiskImagePersistence;
import emu.jvic.io.disk.persistence.DiskImagePersistenceSession;
import emu.jvic.io.disk.persistence.DiskPersistenceKey;
import emu.jvic.io.disk.persistence.DiskPersistenceMetadata;
import emu.jvic.io.disk.persistence.DiskPersistenceSupport;

/**
 * GWT worker-side disk image persistence backed by OPFS.
 */
public class GwtOpfsDiskImagePersistence implements DiskImagePersistence {

    private interface ResolveCallback {
        void onResolved(ArrayBuffer startupDiskImage, boolean persistent);
    }

    @Override
    public void resolve(AppConfigItem appConfigItem, byte[] originalDiskImage,
            Consumer<DiskImagePersistenceSession> onResolved) {
        DiskPersistenceKey key = DiskPersistenceSupport.createKey(appConfigItem, originalDiskImage);
        String programIdSource = DiskPersistenceSupport.getProgramIdSource(appConfigItem);
        JavaScriptObject state = createState();
        resolveStartupDisk(state, key.getProgramKey(), key.getOriginalDiskHash(),
                new ResolveCallback() {
                    @Override
                    public void onResolved(ArrayBuffer startupDiskImage, boolean persistent) {
                        byte[] resolvedDiskImage = (startupDiskImage != null)
                                ? toByteArray(startupDiskImage)
                                : originalDiskImage;
                        onResolved.accept(new GwtOpfsDiskImagePersistenceSession(state,
                                appConfigItem, key, programIdSource, originalDiskImage,
                                resolvedDiskImage, persistent));
                    }
                });
    }

    private byte[] toByteArray(ArrayBuffer arrayBuffer) {
        Uint8Array source = TypedArrays.createUint8Array(arrayBuffer);
        byte[] data = new byte[arrayBuffer.byteLength()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)(source.get(i) & 0xff);
        }
        return data;
    }

    private ArrayBuffer toArrayBuffer(byte[] data) {
        ArrayBuffer arrayBuffer = TypedArrays.createArrayBuffer(data.length);
        Uint8Array target = TypedArrays.createUint8Array(arrayBuffer);
        for (int i = 0; i < data.length; i++) {
            target.set(i, data[i] & 0xff);
        }
        return arrayBuffer;
    }

    private native JavaScriptObject createState() /*-{
        return {
            writeChain: Promise.resolve(),
            closed: false
        };
    }-*/;

    private native void resolveStartupDisk(JavaScriptObject state, String programKey,
            String originalDiskHash, ResolveCallback callback) /*-{
        var finish = function(buffer, persistent) {
            callback.@emu.jvic.gwt.disk.GwtOpfsDiskImagePersistence.ResolveCallback::onResolved(Lcom/google/gwt/typedarrays/shared/ArrayBuffer;Z)(buffer, persistent);
        };
        var openExistingDir = function(parent, name) {
            return parent.getDirectoryHandle(name)['catch'](function() {
                return null;
            });
        };
        var openExistingFile = function(parent, name) {
            return parent.getFileHandle(name)['catch'](function() {
                return null;
            });
        };

        if (!(self.navigator && self.navigator.storage && self.navigator.storage.getDirectory)) {
            finish(null, false);
            return;
        }

        self.navigator.storage.getDirectory()
            .then(function(root) {
                return openExistingDir(root, 'JVic');
            })
            .then(function(jvicDir) {
                if (!jvicDir) {
                    return null;
                }
                return openExistingDir(jvicDir, 'Disk Images');
            })
            .then(function(diskImagesDir) {
                if (!diskImagesDir) {
                    return null;
                }
                return openExistingDir(diskImagesDir, 'v1');
            })
            .then(function(versionDir) {
                if (!versionDir) {
                    return null;
                }
                return openExistingDir(versionDir, programKey);
            })
            .then(function(programDir) {
                if (!programDir) {
                    return null;
                }
                return openExistingDir(programDir, originalDiskHash);
            })
            .then(function(imageDir) {
                if (!imageDir) {
                    return null;
                }
                return openExistingFile(imageDir, 'disk.d64');
            })
            .then(function(fileHandle) {
                if (!fileHandle) {
                    finish(null, false);
                    return null;
                }
                return fileHandle.getFile()
                    .then(function(file) {
                        return file.arrayBuffer();
                    })
                    .then(function(buffer) {
                        finish(buffer, true);
                    });
            })
            ['catch'](function(error) {
                console.error('JVic GWT OPFS resolve failed', error);
                finish(null, false);
            });
    }-*/;

    private native void queueWrite(JavaScriptObject state, String programKey,
            String originalDiskHash, ArrayBuffer diskImageData, String metadataJson) /*-{
        var ensureDir = function(parent, name) {
            return parent.getDirectoryHandle(name, {create: true});
        };
        var writeFile = function(fileHandle, contents) {
            return fileHandle.createWritable().then(function(writable) {
                return writable.write(contents).then(function() {
                    return writable.close();
                }, function(error) {
                    writable.abort();
                    throw error;
                });
            });
        };

        state.writeChain = (state.writeChain || Promise.resolve())
            ['catch'](function() {
            })
            .then(function() {
                if (state.closed) {
                    return null;
                }
                return self.navigator.storage.getDirectory()
                    .then(function(root) {
                        return ensureDir(root, 'JVic');
                    })
                    .then(function(jvicDir) {
                        return ensureDir(jvicDir, 'Disk Images');
                    })
                    .then(function(diskImagesDir) {
                        return ensureDir(diskImagesDir, 'v1');
                    })
                    .then(function(versionDir) {
                        return ensureDir(versionDir, programKey);
                    })
                    .then(function(programDir) {
                        return ensureDir(programDir, originalDiskHash);
                    })
                    .then(function(imageDir) {
                        return Promise.all([
                            imageDir.getFileHandle('disk.d64', {create: true}),
                            imageDir.getFileHandle('meta.json', {create: true})
                        ]);
                    })
                    .then(function(handles) {
                        return Promise.all([
                            writeFile(handles[0], diskImageData),
                            writeFile(handles[1], metadataJson)
                        ]);
                    });
            })
            ['catch'](function(error) {
                console.error('JVic GWT OPFS write failed', error);
            });
    }-*/;

    private native void closeState(JavaScriptObject state) /*-{
        if (state) {
            state.closed = true;
        }
    }-*/;

    private class GwtOpfsDiskImagePersistenceSession implements DiskImagePersistenceSession {

        private final JavaScriptObject state;
        private final AppConfigItem appConfigItem;
        private final DiskPersistenceKey key;
        private final String programIdSource;
        private final int originalDiskSize;
        private final byte[] startupDiskImage;
        private boolean persistent;
        private long createdAtEpochMs;
        private long persistenceActivatedAtEpochMs;

        GwtOpfsDiskImagePersistenceSession(JavaScriptObject state, AppConfigItem appConfigItem,
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