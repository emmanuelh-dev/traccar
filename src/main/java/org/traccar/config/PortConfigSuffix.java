package org.traccar.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortConfigSuffix extends ConfigSuffix<Integer> {

    private static final Map<String, Integer> PORTS = new HashMap<>();

    static {
        PORTS.put("xexun", 5006);
        PORTS.put("xexun2", 5233);
        PORTS.put("xexun3", 5257);
    }

    PortConfigSuffix(String key, List<KeyType> types) {
        super(key, types, null);
    }

    @Override
    public ConfigKey<Integer> withPrefix(String protocol) {
        return new IntegerConfigKey(protocol + keySuffix, types, PORTS.get(protocol));
    }
}
