package emu.jvic.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The labels of one memspace: names such as ".loop" mapped to addresses. Several
 * labels may share an address; disassembly shows the one added last, as VICE does.
 */
final class LabelTable {

    private final Map<String, Integer> byName = new LinkedHashMap<>();
    private final Map<Integer, String> byAddress = new HashMap<>();

    /** Adds a label, or moves it if it already exists. */
    void add(String name, int address) {
        remove(name);
        address &= 0xFFFF;
        byName.put(name, address);
        byAddress.put(address, name);
    }

    /** Removes a label. Returns false if there was none of that name. */
    boolean remove(String name) {
        Integer address = byName.remove(name);
        if (address == null) {
            return false;
        }
        if (name.equals(byAddress.get(address))) {
            byAddress.remove(address);
            // Another label at the same address takes over: the last one added.
            for (Map.Entry<String, Integer> entry : byName.entrySet()) {
                if (entry.getValue().intValue() == address) {
                    byAddress.put(address, entry.getKey());
                }
            }
        }
        return true;
    }

    /** The address of a label, or null. */
    Integer get(String name) {
        return byName.get(name);
    }

    /** The label shown for an address, or null. */
    String nameAt(int address) {
        return byAddress.isEmpty() ? null : byAddress.get(address & 0xFFFF);
    }

    boolean isEmpty() {
        return byName.isEmpty();
    }

    void clear() {
        byName.clear();
        byAddress.clear();
    }

    /**
     * All labels, sorted by address and then by when they were added. (VICE lists
     * them in its hash table's order.)
     */
    List<Map.Entry<String, Integer>> sorted() {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(byName.entrySet());
        entries.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
        return entries;
    }
}
