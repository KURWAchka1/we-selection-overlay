import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import com.google.gson.GsonBuilder;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Offline reader for owned fixture YAML only. No Paper initialization or live access. */
class PaperProfilerConfiguration {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 4) throw new IllegalArgumentException("Expected one to four fixture configurations");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(20); options.setCodePointLimit(1_048_576);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        var configurations = new ArrayList<Object>();
        for (String argument : args) {
            Path path = Path.of(argument);
            if (Files.size(path) > 1_048_576) throw new IllegalArgumentException("Fixture YAML exceeds reader bound");
            Object value;
            try (var reader = Files.newBufferedReader(path)) { value = yaml.load(reader); }
            validate(value);
            if (!(value instanceof Map<?, ?> config) || !Integer.valueOf(31).equals(config.get("_version"))
                    || !(config.get("spark") instanceof Map<?, ?> spark)
                    || !(spark.get("enabled") instanceof Boolean)
                    || !Boolean.FALSE.equals(spark.get("enable-immediately")))
                throw new IllegalArgumentException("Invalid effective profiler configuration");
            configurations.add(value);
        }
        System.out.println(new GsonBuilder().serializeNulls().create().toJson(configurations));
    }

    private static void validate(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("Non-string YAML key");
                validate(entry.getValue());
            }
        } else if (value instanceof java.util.List<?> list) {
            for (Object item : list) validate(item);
        } else if (value != null && !(value instanceof String) && !(value instanceof Boolean) && !(value instanceof Number)) {
            throw new IllegalArgumentException("Non-JSON YAML value");
        } else if (value instanceof Number number && !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Non-finite YAML value");
        }
    }
}
