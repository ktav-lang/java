package examples;

import lang.ktav.Ktav;
import lang.ktav.Value;

import java.util.LinkedHashMap;
import java.util.List;

public final class Basic {
    public static void main(String[] args) {
        String src = """
                service: web
                port:i 8080
                tls: true
                tags: [
                    prod
                    eu-west-1
                ]
                """;

        Value doc = Ktav.loads(src);
        System.out.println("parsed: " + doc);

        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put("name", new Value.Str("demo"));
        entries.put("count", Value.Int.of(42));
        entries.put("tags", new Value.Arr(List.of(new Value.Str("a"), new Value.Str("b"))));

        String text = Ktav.dumps(new Value.Obj(entries));
        System.out.println("---");
        System.out.println(text);
    }

    private Basic() {
    }
}
