package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public class ReadResources {
    static List<String> readLinesFromExternalFile(String resourceName) throws IOException {
        try (InputStream resourceAsStream = Main.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceAsStream == null) {
                throw new IllegalArgumentException(resourceName + " could not be found");
            }
            return new BufferedReader(new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    static Map<String, List<String>> readMemberMapFromExternalFile(String resourceName) throws IOException {
        return readLinesFromExternalFile(resourceName).stream()
                .map(String::toLowerCase)
                .map(line -> line.split(":"))
                .collect(groupingBy(groupAndUserEmail -> groupAndUserEmail[0],
                        mapping(strings -> strings[1], toList())
                ));

    }
}
