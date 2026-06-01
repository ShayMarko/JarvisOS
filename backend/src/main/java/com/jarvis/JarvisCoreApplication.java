package com.jarvis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class JarvisCoreApplication {

	public static void main(String[] args) {
		ensureDatabaseDirectory();
		SpringApplication.run(JarvisCoreApplication.class, args);
	}

	/**
	 * SQLite will not create the parent directory of its database file, and the
	 * datasource connects before any Spring bean runs — so create it here first.
	 */
	private static void ensureDatabaseDirectory() {
		String dbPath = System.getenv().getOrDefault("JARVIS_DB_PATH", "./data/jarvis.db");
		Path parent = Path.of(dbPath).toAbsolutePath().getParent();
		if (parent != null) {
			try {
				Files.createDirectories(parent);
			} catch (IOException e) {
				throw new UncheckedIOException("Could not create database directory: " + parent, e);
			}
		}
	}

}
