package fau.fdm.OntoFormGenerator;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@SpringBootApplication
public class OntoFormGeneratorApplication implements WebMvcConfigurer {

	private static ConfigurableApplicationContext context;
	private static String[] arguments;

	private static final String ontologyDirectory = "ontologies/production";

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");
		arguments = args;
		context = SpringApplication.run(OntoFormGeneratorApplication.class, args);
	}

	public static void restart(boolean deleteDb) {
		Thread thread = new Thread(() -> {
			context.close();
			if (deleteDb) {
				try {
					Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
					dataset.begin(ReadWrite.WRITE);
					dataset.listModelNames().forEachRemaining(dataset::removeNamedModel);
					dataset.commit();
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
			context = SpringApplication.run(OntoFormGeneratorApplication.class, arguments);
		});
		thread.setDaemon(false);
		thread.start();
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		UrlPathHelper urlPathHelper = new UrlPathHelper();
		urlPathHelper.setUrlDecode(false);
		configurer.setUrlPathHelper(urlPathHelper);
	}

}
