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
        List<String> groupsToSync = ReadResources.readLinesFromExternalFile("groupsToSync");
        List<String> putAllMembersIn = ReadResources.readLinesFromExternalFile("putAllMembersIn");

        Directory service = CredentialHelper.getDirectoryClient();

        List<Group> groups = SyncLists.getAllGroups(service);
        Map<String, List<Group>> emailToGroupMap = groups.stream().collect(groupingBy(group -> getUserFromEmail(group.getEmail().toLowerCase())));

        Set<String> usersToPutOrKeepInGroup = memberMapFromExternalFile.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

        Group groupForAllUsers = emailToGroupMap.get(putAllMembersIn.get(0)).get(0);

        SyncLists.pretendSync(service, usersToPutOrKeepInGroup, groupForAllUsers);

        groupsToSync.forEach(groupEmail -> {
            Group groupToSync = emailToGroupMap.get(groupEmail).get(0);
            List<String> usersToKeepInGroup = memberMapFromExternalFile.computeIfAbsent(groupEmail, s -> new ArrayList<>());
            SyncLists.pretendSync(service, usersToKeepInGroup, groupToSync);
        });

        System.out.println("check output above for errors before proceeding");

        UserAction userAction = UserConfirmationDialog.askUserForInput();

        switch (userAction) {
            case EXIT:
                System.out.println("exiting program");
                break;
            case PROCEED:
                System.out.println("applying changes");
                SyncLists.sync(service, usersToPutOrKeepInGroup, groupForAllUsers);

                groupsToSync.forEach(groupEmail -> {
                    Group groupToSync = emailToGroupMap.get(groupEmail).get(0);
                    List<String> usersToKeepInGroup = memberMapFromExternalFile.computeIfAbsent(groupEmail, s -> new ArrayList<>());
                    SyncLists.sync(service, usersToKeepInGroup, groupToSync);
                });
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + userAction);
        }
    }

    private static String getUserFromEmail(String email) {
        return email.split("@")[0];
    }
}