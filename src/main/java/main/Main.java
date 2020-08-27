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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

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
        List<String> groupsToSync = readLinesFromExternalFile("groupsToSync");
        List<String> putAllMembersIn = readLinesFromExternalFile("putAllMembersIn");

        Directory service = getDirectoryClient();

        List<Group> groups = getAllGroups(service);
        Map<String, List<Group>> emailToGroupMap = groups.stream().collect(groupingBy(group -> getUserFromEmail(group.getEmail().toLowerCase())));

        Set<String> usersToPutOrKeepInGroup = memberMapFromExternalFile.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

        Group groupForAllUsers = emailToGroupMap.get(putAllMembersIn.get(0)).get(0);

        pretendSync(service, usersToPutOrKeepInGroup, groupForAllUsers);

        groupsToSync.forEach(groupEmail -> {
            Group groupToSync = emailToGroupMap.get(groupEmail).get(0);
            List<String> usersToKeepInGroup = memberMapFromExternalFile.computeIfAbsent(groupEmail, s -> new ArrayList<>());
            pretendSync(service, usersToKeepInGroup, groupToSync);
        });

        System.out.println("check output above for errors before proceeding");

        UserAction userAction = askUserInput();

        switch (userAction) {
            case EXIT:
                System.out.println("exiting program");
                break;
            case PROCEED:
                System.out.println("applying changes");
                sync(service, usersToPutOrKeepInGroup, groupForAllUsers);

                groupsToSync.forEach(groupEmail -> {
                    Group groupToSync = emailToGroupMap.get(groupEmail).get(0);
                    List<String> usersToKeepInGroup = memberMapFromExternalFile.computeIfAbsent(groupEmail, s -> new ArrayList<>());
                    sync(service, usersToKeepInGroup, groupToSync);
                });
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + userAction);
        }
    }

    private static UserAction askUserInput() {
        String randomAlphanumeric = RandomStringUtils.randomAlphanumeric(20);
        String userInput = null;
        try (Scanner scanner = new Scanner(System.in)) {
            while (!userConfirmsToProceed(randomAlphanumeric, userInput) && !userExits(userInput)) {
                String message = String.format("to proceed enter [ %s ] or enter exit to cancel the program %n", randomAlphanumeric);
                System.out.println(message);
                userInput = scanner.nextLine();
            }
        }

        if (userExits(userInput)) {
            return UserAction.EXIT;
        } else if (userConfirmsToProceed(randomAlphanumeric, userInput)) {
            return UserAction.PROCEED;
        }
        return UserAction.INVALID;
    }

    private static boolean userExits(String userInput) {
        return "exit".equals(userInput);
    }

    private static boolean userConfirmsToProceed(String randomAlphanumeric, String userInput) {
        return randomAlphanumeric.equals(userInput);
    }

    private static void pretendSync(Directory service, Collection<String> usersToPutOrKeepInGroup, Group group) {
        Map<String, List<Member>> currentGroupMembersByEmail = getMembers(service, group).stream()
                .collect(groupingBy(member -> member.getEmail().toLowerCase())); //toLower is important to avoid mismatches
        Set<String> emailsOfCurrentGroupMembers = currentGroupMembersByEmail.keySet();

        List<String> toInsert = new ArrayList<>(CollectionUtils.subtract(usersToPutOrKeepInGroup, emailsOfCurrentGroupMembers));
        List<String> toDelete = new ArrayList<>(CollectionUtils.subtract(emailsOfCurrentGroupMembers, usersToPutOrKeepInGroup));

        Collections.sort(toInsert);
        Collections.sort(toDelete);

        System.out.println(group.getEmail());
        toDelete.forEach(s -> System.out.println("DELETE " + s));
        toInsert.forEach(s -> System.out.println("INSERT " + s));
        System.out.println();
    }

    private static void sync(Directory service, Collection<String> usersToPutOrKeepInGroup, Group group) {
        Map<String, List<Member>> currentGroupMembersByEmail = getMembers(service, group).stream()
                .collect(groupingBy(member -> member.getEmail().toLowerCase())); //toLower is important to avoid mismatches
        Set<String> emailsOfCurrentGroupMembers = currentGroupMembersByEmail.keySet();

        List<String> toInsert = new ArrayList<>(CollectionUtils.subtract(usersToPutOrKeepInGroup, emailsOfCurrentGroupMembers));
        List<String> toDelete = new ArrayList<>(CollectionUtils.subtract(emailsOfCurrentGroupMembers, usersToPutOrKeepInGroup));

        Collections.sort(toInsert);
        Collections.sort(toDelete);

        String groupKey = group.getEmail();
        System.out.println(groupKey);
        toDelete.forEach(userToDelete -> deleteUser(service, groupKey, userToDelete));
        toInsert.forEach(userToInsert -> insertUser(service, groupKey, userToInsert));

        System.out.println();
    }

    private static void insertUser(Directory service, String groupKey, String emailToInsert) {
        try {
            System.out.printf("inserting email %s into group %s%n", emailToInsert, groupKey);
            Member toInsert = new Member();
            toInsert.setEmail(emailToInsert);
            service.members().insert(groupKey, toInsert);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteUser(Directory service, String groupKey, String emailToDelete) {
        try {
            System.out.printf("removing email %s from group %s%n", emailToDelete, groupKey);
            service.members().delete(groupKey, emailToDelete).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getUserFromEmail(String email) {
        return email.split("@")[0];
    }

    private static List<Group> getAllGroups(Directory service) throws IOException {
        return service.groups().list()
                .setCustomer("my_customer")
                .setMaxResults(100)
                .execute().getGroups();
    }

    private static List<String> readLinesFromExternalFile(String resourceName) throws IOException {
        try (InputStream resourceAsStream = Main.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceAsStream == null) {
                throw new IllegalArgumentException(resourceName + " could not be found");
            }
            return new BufferedReader(new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    private static List<Member> getMembers(Directory service, Group group) {
        try {
            return service.members().list(group.getEmail()).execute().getMembers();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Build a new authorized API client service.
    private static Directory getDirectoryClient() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private static Map<String, List<String>> readMemberMapFromExternalFile(String resourceName) throws IOException {
        return readLinesFromExternalFile(resourceName).stream()
                .map(String::toLowerCase)
                .map(line -> line.split(":"))
                .collect(groupingBy(groupAndUserEmail -> groupAndUserEmail[0],
                        mapping(strings -> strings[1], toList())
                ));

    }
}