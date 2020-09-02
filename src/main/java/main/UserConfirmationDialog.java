package main;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.Scanner;

public class UserConfirmationDialog {
    static UserAction askUserForInput() {
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
}
