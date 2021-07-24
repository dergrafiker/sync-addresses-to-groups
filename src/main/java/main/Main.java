package main;

import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Group;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

public class Main {

    public static void main(String... args) throws IOException, GeneralSecurityException {
        Map<String, List<String>> memberMapFromExternalFile = ReadResources.readMemberMapFromExternalFile("mapping");
        String listToPutAllMembersIn = expectSingleItem(ReadResources.readLinesFromExternalFile("putAllMembersIn"));

        Directory service = CredentialHelper.getDirectoryClient();

        List<Group> allGroups = SyncLists.getAllGroups(service);
        Map<String, List<Group>> emailToGroupMap = allGroups.stream()
                .collect(groupingBy(group -> getUserPartFromEmail(group.getEmail().toLowerCase())));

        Set<String> allUsers = memberMapFromExternalFile.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

        Group groupForAllUsers = expectSingleItem(emailToGroupMap.get(listToPutAllMembersIn));

        SyncLists.pretendSync(service, allUsers, groupForAllUsers);

        memberMapFromExternalFile.keySet().forEach(groupEmail -> {
            List<Group> matchingLists = emailToGroupMap.get(groupEmail);
            if (matchingLists != null && !matchingLists.isEmpty()) {
                Group groupToSync = expectSingleItem(matchingLists);
                List<String> usersToKeepInGroup = memberMapFromExternalFile.computeIfAbsent(groupEmail, s -> new ArrayList<>());
                SyncLists.pretendSync(service, usersToKeepInGroup, groupToSync);
            } else {
                System.out.println(groupEmail + " was not found remote");
                System.out.println();
            }
        });

        System.out.println("check output above for errors before proceeding");

        UserAction userAction = UserConfirmationDialog.askUserForInput();

        switch (userAction) {
            case EXIT:
                System.out.println("exiting program");
                break;
            case PROCEED:
                System.out.println("applying changes");
                SyncLists.sync(service, allUsers, groupForAllUsers);

                memberMapFromExternalFile.keySet().forEach(groupEmail -> {
                    List<Group> list = emailToGroupMap.get(groupEmail);
                    try {
                        Group groupToSync = expectSingleItem(list);
                        List<String> usersToKeepInGroup = memberMapFromExternalFile.computeIfAbsent(groupEmail, s -> new ArrayList<>());
                        SyncLists.sync(service, usersToKeepInGroup, groupToSync);
                    } catch (IllegalArgumentException e) {
                        System.out.printf("Skipping list %s. Error was '%s'%n", groupEmail, e.getMessage());
                    }
                });
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + userAction);
        }
    }

    private static <T> T expectSingleItem(List<T> list) {
        if (list != null && list.size() == 1) {
            return list.get(0);
        } else if (list == null || list.size() == 0) {
            throw new IllegalArgumentException("list must not be empty");
        } else {
            throw new IllegalArgumentException("list must have exactly one item");
        }
    }

    private static String getUserPartFromEmail(String email) {
        return email.split("@")[0];
    }
}