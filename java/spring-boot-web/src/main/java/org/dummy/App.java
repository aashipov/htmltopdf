package org.dummy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * Entrypoint.
 */
@ServletComponentScan
@SpringBootApplication
public class App {

	public static void main(String[] args) {
		HtmlToPdfUtils.restartChromiumHeadless();
		SpringApplication.run(App.class, args);
	}

}
