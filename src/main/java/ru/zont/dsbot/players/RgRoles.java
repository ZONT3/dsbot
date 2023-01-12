package ru.zont.dsbot.players;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.zont.dsbot.core.util.DescribedException;
import ru.zont.dsbot.core.util.LiteJSON;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RgRoles {
    private static final LiteJSON db = new LiteJSON("known_roles");

    public static HashMap<String, Integer> getKnownRoles() {
        return db.op(root -> {
            HashMap<String, Integer> res = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet())
                res.put(e.getValue().getAsString(), Integer.parseInt(e.getKey()));
            return res;
        });
    }

    public static int getRoleId(String role) {
        HashMap<String, Integer> knownRoles = getKnownRoles();
        if (!knownRoles.containsKey(role))
            throw new DescribedException("Cannot find role id for that name");
        return knownRoles.get(role);
    }

    public static void addRole(int id, String name) {
        db.op((Consumer<JsonObject>) root -> root.addProperty(id + "", name));
    }
}
