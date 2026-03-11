/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: The Spring Boot application entry point.                             ║
 * ║                                                                              ║
 * ║  WHY:  Every Spring Boot app needs exactly ONE class with a main() method    ║
 * ║        and the @SpringBootApplication annotation.  This is the class that   ║
 * ║        starts the embedded Tomcat server, scans for components, connects     ║
 * ║        to the database, and boots up the entire application.                 ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - You almost never need to change this file.                          ║
 * ║        - To add startup logic (seed data, etc.), create a bean that          ║
 * ║          implements CommandLineRunner in a separate class.                   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * @SpringBootApplication is a convenience annotation that combines three things:
 *
 *   @Configuration     — This class can define @Bean methods (Java-based config).
 *   @EnableAutoConfiguration — Spring Boot auto-configures beans based on the
 *                              jars on your classpath (e.g., it sees mysql-connector
 *                              and spring-data-jpa, so it auto-configures a
 *                              DataSource and an EntityManager).
 *   @ComponentScan     — Scans this package (com.ipos) and all sub-packages
 *                         for classes annotated with @Component, @Service,
 *                         @Repository, @Controller, etc. and registers them
 *                         as Spring beans (managed objects).
 *
 *   IMPORTANT: This class MUST be in the ROOT package (com.ipos) so that
 *   @ComponentScan finds all classes in sub-packages like com.ipos.entity,
 *   com.ipos.service, com.ipos.controller, etc.
 */
@SpringBootApplication
public class IposApplication {

    public static void main(String[] args) {
        /*
         * SpringApplication.run() does ALL the heavy lifting:
         *   1. Creates the Spring ApplicationContext (the IoC container).
         *   2. Scans for and instantiates all beans.
         *   3. Starts the embedded Tomcat server on the configured port.
         *   4. Connects Hibernate to MySQL and runs ddl-auto logic.
         *   5. The app is now ready to receive HTTP requests!
         */
        SpringApplication.run(IposApplication.class, args);
    }
}
