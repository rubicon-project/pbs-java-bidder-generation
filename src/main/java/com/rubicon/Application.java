package com.rubicon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    private static String pbsDirectory;

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        pbsDirectory = args[0];
        SpringApplication.run(Application.class, args);
    }

    public static String getPbsDirectory() {
        return pbsDirectory;
    }
}
