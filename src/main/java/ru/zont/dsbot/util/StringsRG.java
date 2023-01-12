package ru.zont.dsbot.util;

import org.jetbrains.annotations.PropertyKey;
import ru.zont.dsbot.core.util.Strings;

public class StringsRG extends Strings {
    public static final StringsRG STR = new StringsRG();

    public StringsRG() {
        super("strings");
    }

    @Override
    public String get(@PropertyKey(resourceBundle = "strings") String id, Object... args) {
        return super.get(id, args);
    }
}
