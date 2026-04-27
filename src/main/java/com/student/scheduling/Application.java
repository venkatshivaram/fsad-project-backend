package com.student.scheduling;

import com.student.scheduling.entity.User;
import com.student.scheduling.repository.UserRepository;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        configureRailwayDatabase();
        SpringApplication.run(Application.class, args);
    }

    private static void configureRailwayDatabase() {
        if (!isBlank(env("SPRING_DATASOURCE_URL"))) {
            return;
        }

        String mysqlUrl = firstPresent("MYSQL_URL", "MYSQL_PUBLIC_URL");
        if (!isBlank(mysqlUrl)) {
            applyMysqlUrl(mysqlUrl);
            return;
        }

        String host = firstPresent("MYSQL_HOST", "MYSQLHOST");
        if (isBlank(host)) {
            return;
        }

        String port = firstPresent("MYSQL_PORT", "MYSQLPORT");
        String database = firstPresent("MYSQL_DATABASE", "MYSQLDATABASE");
        String user = firstPresent("MYSQL_USER", "MYSQLUSER");
        String password = firstPresent("MYSQL_PASSWORD", "MYSQLPASSWORD", "MYSQL_ROOT_PASSWORD");

        System.setProperty("spring.datasource.url", buildJdbcUrl(host, isBlank(port) ? "3306" : port,
                isBlank(database) ? "railway" : database, null));
        setPropertyIfPresent("spring.datasource.username", user);
        setPropertyIfPresent("spring.datasource.password", password);
    }

    private static void applyMysqlUrl(String mysqlUrl) {
        try {
            URI uri = URI.create(mysqlUrl);
            String database = uri.getPath() == null || uri.getPath().length() <= 1 ? "railway" : uri.getPath().substring(1);
            System.setProperty("spring.datasource.url", buildJdbcUrl(uri.getHost(), String.valueOf(uri.getPort()),
                    database, uri.getQuery()));

            String userInfo = uri.getUserInfo();
            if (!isBlank(userInfo)) {
                String[] credentials = userInfo.split(":", 2);
                setPropertyIfPresent("spring.datasource.username", decode(credentials[0]));
                if (credentials.length > 1) {
                    setPropertyIfPresent("spring.datasource.password", decode(credentials[1]));
                }
            }
        } catch (IllegalArgumentException ex) {
            System.setProperty("spring.datasource.url", mysqlUrl.replaceFirst("^mysql://", "jdbc:mysql://"));
        }
    }

    private static String buildJdbcUrl(String host, String port, String database, String query) {
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(host)
                .append(":")
                .append(isBlank(port) || "-1".equals(port) ? "3306" : port)
                .append("/")
                .append(database);

        if (!isBlank(query)) {
            url.append("?").append(query).append("&");
        } else {
            url.append("?");
        }

        return url.append("createDatabaseIfNotExist=true")
                .append("&useSSL=false")
                .append("&allowPublicKeyRetrieval=true")
                .append("&serverTimezone=UTC")
                .toString();
    }

    private static String firstPresent(String... names) {
        for (String name : names) {
            String value = env(name);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static void setPropertyIfPresent(String property, String value) {
        if (!isBlank(value)) {
            System.setProperty(property, value);
        }
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Bean
    CommandLineRunner normalizeLegacyInstructorUsers(UserRepository userRepository) {
        return args -> {
            for (User user : userRepository.findByRole("instructor")) {
                user.setRole("admin");
                userRepository.save(user);
            }
        };
    }
}
