package main;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Member;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SyncLists {

    static void pretendSync(Directory service, Collection<String> usersToPutOrKeepInGroup, Group group) {
        Set<String> emailsOfCurrentGroupMembers = collectLowerCaseEmails(getMembers(service, group));

        List<String> toInsert = subtract(usersToPutOrKeepInGroup, emailsOfCurrentGroupMembers);
        List<String> toDelete = subtract(emailsOfCurrentGroupMembers, usersToPutOrKeepInGroup);

        System.out.println(group.getEmail());
        toDelete.forEach(s -> System.out.println("DELETE " + s));
        toInsert.forEach(s -> System.out.println("INSERT " + s));
        System.out.println();
    }

    private static <T extends Comparable<T>> List<T> subtract(Collection<T> base, Collection<T> remove) {
        List<T> c = new ArrayList<>(base);
        c.removeAll(remove);
        Collections.sort(c);
        return c;
    }

    private static Set<String> collectLowerCaseEmails(List<Member> members) {
        return members.stream().map(member -> member.getEmail().toLowerCase()).collect(Collectors.toSet());
    }

    static void sync(Directory service, Collection<String> usersToPutOrKeepInGroup, Group group) {
        Set<String> emailsOfCurrentGroupMembers = collectLowerCaseEmails(getMembers(service, group));

        List<String> toInsert = subtract(usersToPutOrKeepInGroup, emailsOfCurrentGroupMembers);
        List<String> toDelete = subtract(emailsOfCurrentGroupMembers, usersToPutOrKeepInGroup);

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
            service.members().insert(groupKey, toInsert).execute();
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

    static List<Group> getAllGroups(Directory service) throws IOException {
        return service.groups().list()
                .setCustomer("my_customer")
//                .setMaxResults(100)
                .execute().getGroups();
    }

    private static List<Member> getMembers(Directory service, Group group) {
        try {
            return Optional.ofNullable(service.members().list(group.getEmail()).execute().getMembers())
                    .orElse(Collections.emptyList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
