package l9g.account.info;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import l9g.account.info.crypto.CryptoHandler;
import l9g.account.info.crypto.PasswordGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class Application
{
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

  @Bean
  public CommandLineRunner commandLineRunner(DataSource dataSource)
  {
    return args ->
    {
      HikariDataSource hikariDataSource = (HikariDataSource)dataSource;
      log.info("--- Database Info -------------------------------");

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
      log.debug("Username: " + hikariDataSource.getUsername());
      log.trace("Password: " + hikariDataSource.getPassword());

      log.info("-------------------------------------------------");
    };
  }

}
