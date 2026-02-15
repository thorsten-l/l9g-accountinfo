/*
 * Copyright 2026 Thorsten Ludewig (t.ludewig@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package l9g.account.info;

import com.zaxxer.hikari.HikariDataSource;
import de.l9g.crypto.core.CryptoHandler;
import de.l9g.crypto.core.PasswordGenerator;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;

/**
 * Main entry point for the l9g-accountinfo Spring Boot application.
 * This class initializes and runs the application, handling command-line arguments
 * for encryption, token generation, and help information. It also provides
 * a CommandLineRunner bean to log database and connection pool information on startup.
 */
@SpringBootApplication
@Slf4j
public class Application
{
  /**
   * Main method to start the l9g-accountinfo application.
   * It also handles several command-line arguments for utility functions:
   * <ul>
   * <li>`-e <clear text>`: Encrypts the provided clear text.</li>
   * <li>`-g`: Generates a new encrypted token.</li>
   * <li>`-i`: Initializes `data/secret.bin`.</li>
   * <li>`-h`: Displays help information.</li>
   * </ul>
   *
   * @param args Command-line arguments.
   */
  public static void main(String[] args)
  {
    if(args != null)
    {
      CryptoHandler cryptoHandler = CryptoHandler.getInstance();

      if(args.length == 2 && "-e".equals(args[0]))
      {
        System.out.println(args[1] + " = \"" + cryptoHandler.encrypt(args[1]) + "\"");
        System.exit(0);
      }

      if(args.length == 1 && "-g".equals(args[0]))
      {
        String token = PasswordGenerator.generate(32);
        System.out.println("\"" + token + "\" = \"" + cryptoHandler.encrypt(token) + "\"");
        System.exit(0);
      }

      if(args.length == 1 && "-i".equals(args[0]))
      {
        cryptoHandler.encrypt("init");
        System.out.println("Initialize data/secret.bin");
        System.exit(0);
      }

      if(args.length == 1 && "-h".equals(args[0]))
      {
        System.out.println("l9g-accountinfo [-e clear text] [-g] [-h]");
        System.out.println("  -e : encrypt clear text");
        System.out.println("  -g : generate new token");
        System.out.println("  -i : initialize data/secret.bin");
        System.out.println("  -h : this help");
        System.exit(0);
      }
    }

    SpringApplication.run(Application.class, args);
  }

  /**
   * Provides a {@link org.springframework.boot.CommandLineRunner} bean to execute code
   * after the application starts up. This runner logs database and connection pool details.
   *
   * @param dataSource The {@link javax.sql.DataSource} automatically injected by Spring.
   *
   * @return A {@link org.springframework.boot.CommandLineRunner} instance.
   */
  @Bean
  public CommandLineRunner commandLineRunner(DataSource dataSource, BuildProperties buildProperties)
  {
    return args ->
    {
      log.info("");
      log.info("");
      log.info("--- Application Info ----------------------------");
      log.info("Name: {}", buildProperties.getName());
      log.info("Version: {}", buildProperties.getVersion());
      log.info("Build: {}", buildProperties.getTime());
      log.info("--- Database Info -------------------------------");

      HikariDataSource hikariDataSource = (HikariDataSource)dataSource;
      try(Connection connection = dataSource.getConnection())
      {
        DatabaseMetaData metaData = connection.getMetaData();
        log.info("Database JDBC URL [Connecting through datasource '"
          + hikariDataSource.getClass().getSimpleName() + " (" + hikariDataSource.getPoolName() + ")']");
        log.info("Database driver: " + metaData.getDriverName());
        log.info("Database version: " + metaData.getDatabaseProductVersion());
      }
      catch(Exception e)
      {
        log.error("Could not retrieve database metadata", e);
      }

      log.info("--- Pool Info -----------------------------------");
      log.info("Pool Name: " + hikariDataSource.getPoolName());
      log.info("Auto Commit: " + hikariDataSource.isAutoCommit());
      log.info("Transaction Isolation: " + hikariDataSource.getTransactionIsolation());
      log.info("Minimum Idle: " + hikariDataSource.getMinimumIdle());
      log.info("Maximum Pool Size: " + hikariDataSource.getMaximumPoolSize());

      log.debug("JDBC URL: " + hikariDataSource.getJdbcUrl());

      log.info("-------------------------------------------------");
    };
  }

}
