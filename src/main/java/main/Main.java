package main;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.DirectoryScopes;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Member;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public class Main {
    private static final String APPLICATION_NAME = "Google Admin SDK Directory API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(DirectoryScopes.ADMIN_DIRECTORY_GROUP, DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {

        Map<String, List<String>> memberMapFromExternalFile = readMemberMapFromExternalFile("mapping");

        Directory service = getDirectoryClient();

        List<Group> groups = service.groups().list()
                .setCustomer("my_customer")
                .setMaxResults(100)
                .execute().getGroups();

        groups.forEach(group -> {
            if (groupIsPresentInMemberMap(group, memberMapFromExternalFile)) {
                String groupEmail = group.getEmail();
                System.out.println("group " + groupEmail);
                try {
                    List<Member> members = service.members().list(groupEmail).execute().getMembers();
                    members.forEach(member -> System.out.println(member.getEmail()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println("###");
                System.out.println();
            }
        });
    }

    private static boolean groupIsPresentInMemberMap(Group group, Map<String, List<String>> memberMapFromExternalFile) {
        String beforeAt = group.getEmail().split("@")[0];
        return memberMapFromExternalFile.containsKey(beforeAt);
    }

    // Build a new authorized API client service.
    private static Directory getDirectoryClient() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private static Map<String, List<String>> readMemberMapFromExternalFile(String resourceName) throws IOException {
        try (InputStream mapping = Main.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (mapping == null) {
                throw new IllegalArgumentException(resourceName + " could not be found");
            }
            return new BufferedReader(new InputStreamReader(mapping, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::toLowerCase)
                    .map(line -> line.split(":"))
                    .collect(groupingBy(groupAndUserEmail -> groupAndUserEmail[0],
                            mapping(strings -> strings[1], toList())
                    ));
        }
    }
}