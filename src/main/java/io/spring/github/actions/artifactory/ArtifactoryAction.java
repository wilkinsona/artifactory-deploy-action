package io.spring.github.actions.artifactory;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArtifactoryAction {

	public static void main(String[] args) {
		System.out.println(Arrays.toString(args));
		System.getenv().forEach((key, value) -> System.out.println(key + "=" + value));
	}

}
